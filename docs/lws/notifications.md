# Notifications

The storage implements the LWS notifications profile: an agent subscribes a **webhook** to one or more
topics, and the storage `POST`s a signed activity notification to that inbox whenever a subscribed
resource changes. Deliveries are signed with **RFC 9421 HTTP Message Signatures** so the receiver can
verify they genuinely came from the storage.

## Subscribing

```
POST {root}.notifications/subscriptions
Authorization: Bearer <jwt>
Content-Type: application/lws+json

{
  "type": "WebhookSubscription",
  "inbox": "https://client.example/inbox",
  "topic": ["https://localhost:8888/W3Clws/"],
  "expires": "2026-12-31T00:00:00Z"
}
```

- `type` must be `"WebhookSubscription"` (else `400`).
- `inbox` is required — the absolute URL the storage will `POST` notifications to (else `400`).
- `topic` is a non-empty array of resource URIs to watch (each must be a URI, else `400`). A change to a
  topic — or, for a container topic, to its membership — triggers a delivery.
- `expires` (optional, a date-time) bounds the subscription's lifetime.

**Response: `200 OK`** with a `Location` header naming the created subscription resource. (Note this is
`200`, not the `201` used by resource creation and the access endpoints.)

### Subscribe-time authorization

The subscriber must be able to **read every topic**. A topic the agent may not read is refused with the
*same* message as a topic that does not exist — "not a resource you may subscribe to" — so a subscription
attempt cannot be used to probe for the existence of resources the agent cannot see.

## Managing subscriptions

| Request | Effect |
|---|---|
| `GET {root}.notifications/subscriptions` | A paginated container of **the caller's own** subscriptions. |
| `GET {root}.notifications/subscriptions/{id}` | The subscription document (owner only). |
| `DELETE {root}.notifications/subscriptions/{id}` | Cancels it — delivery stops. |

## The notification envelope

Each delivery is a `POST` to the inbox with this body (ActivityStreams-flavored):

```json
{
  "@context": [
    "https://www.w3.org/ns/lws/v1",
    "https://www.w3.org/ns/activitystreams"
  ],
  "type": "Notification",
  "storage": "https://localhost:8888/W3Clws/",
  "activity": {
    "id": "<uuid>",
    "type": ["Create"],
    "object": { "id": "https://localhost:8888/W3Clws/3f2a…", "type": ["DataResource"] },
    "target": "https://localhost:8888/W3Clws/",
    "published": "2026-07-15T00:31:04.512Z"
  }
}
```

- `activity.type` is one of `Create`, `Update`, or `Delete`:
  - **`Create`** — a `POST` created a child. Emitted with `target` = the parent container.
  - **`Update`** — a `PUT` or `PATCH` changed a resource. Emitted with `target` = the parent.
  - **`Delete`** — a `DELETE` removed a resource. Emitted with **`origin`** (not `target`) = the parent.
- `object.type` is `["DataResource"]` or `["Container"]`.
- `actor` (the WebID of the agent that made the change) is **omitted by default** — the spec says it
  SHOULD be, since telling every subscriber who touched a resource discloses more than the change itself
  does. Set **`:LWSIncludeActor true`** in `settings.ttl` to include it (the spec's "MAY make its
  inclusion configurable").
- **Batching** (`:LWSBatchNotifications true`): a bulk operation delivers its activities in one envelope,
  with `activity` an **array** of activity objects instead of a single object (a spec MAY). A recursive
  `DELETE` uses this to announce the whole removed subtree at once; each subscriber still only ever
  receives the activities for resources it is authorized to read. Off (the default) → a recursive delete
  announces only the container itself, and `activity` is always a single object.

## Verifying the signature (RFC 9421)

The delivery carries `Signature` and `Signature-Input` headers. The signature is **ECDSA P-256 /
SHA-256** (`alg="ecdsa-p256-sha256"`, i.e. JOSE `ES256`), over a signature base covering:

```
@method  @scheme  @authority  @path  content-type  content-digest
```

with signature parameters `created` (Unix seconds), `keyid`, and `alg`. The body is bound in through a
`Content-Digest: sha-256=:<base64>:` header, which is one of the covered components — so the signature
authenticates the body, not just the request line.

The verifying key is published in the **storage description** (`GET {root}.description`), so a receiver
needs no out-of-band key exchange:

```json
"verificationMethod": [
  { "id": "https://localhost:8888/W3Clws/#<kid>",
    "type": "JsonWebKey",
    "controller": "https://localhost:8888/W3Clws/",
    "publicKeyJwk": { "kid": "<kid>", "kty": "EC", "crv": "P-256", "alg": "ES256",
                      "x": "…", "y": "…" } }
],
"authentication": [ "https://localhost:8888/W3Clws/#<kid>" ]
```

- `keyid` in `Signature-Input` matches the JWK's `kid`, which is the **RFC 7638 JWK thumbprint** of the
  public key — a stable identifier that survives restarts (the keypair is persisted, not regenerated on
  boot).
- The raw signature is a `r‖s` pair (P1363 format), not a DER sequence — decode accordingly.

To verify: reconstruct the signature base from the received components in the order listed in
`Signature-Input`, look up the `kid` in the storage description's `verificationMethod`, and verify the
`r‖s` signature with the P-256 public key.

## Delivery, retry, and expiry

- **Immediate retries.** A single delivery is retried up to **3 attempts** with backoff for a transiently
  unreachable inbox before that delivery is counted a failure.
- **Deactivation.** After **5 consecutive failed deliveries**, the subscription is deactivated and
  delivery stops. (This was the case that surfaced the fix where a mis-prefixed subscription id silently
  disabled the failure counter — the counter now uses the bare id.)
- **Expiry.** Once a subscription's `expires` time has passed, the server stops delivering, as the spec
  requires.

## Implementation notes

- Fan-out uses `Executors.newVirtualThreadPerTaskExecutor()` (not `StructuredTaskScope`, which is a
  preview API — the runtime is not started with `--enable-preview`).
- The signing keypair and its `kid` are persisted in the hidden `urn:lws:keys` graph via `SecretStore`
  and primed at startup, so restarts do not rotate the published key. That graph holds a private key and
  is hidden from all client-visible scans.
