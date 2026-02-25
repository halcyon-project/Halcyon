// This code provides functionality to set and retrieve RDF annotation labels using
// SPARQL queries, and to get user credentials and authentication token.

// Module-scoped token — not accessible from window, prevents XSS token theft.
let _token = null;

async function executeSparqlQuery(query, token, isUpdate = false) {
  const endpoint = `${window.location.origin}/rdf`;

  if (!token) {
    token = await getToken(); // This shouldn't happen, but just in case.
  }

  const options = {
    method: 'POST',
    headers: {
      'Content-Type': isUpdate ? 'application/sparql-update' : 'application/sparql-query',
      'Authorization': `Bearer ${token}`
    },
    body: query
  };

  return fetch(endpoint, options)
    .then(response => {
      if (!response.ok) {
        throw new Error(`SPARQL query failed: ${response.statusText}`);
      }
      return response.text();
    })
    .catch(error => {
      console.error('Error executing SPARQL query:', error);
      throw error;
    });
}

// Function to set the annotation label
export function setAnnotationLabel(rdfSubject, newName) {
  const token = _token;

  const sparqlQuery = `
PREFIX sdo: <https://schema.org/>
PREFIX hal: <https://halcyon.is/ns/>
DELETE {
  GRAPH hal:CollectionsAndResources {
    <${rdfSubject}> sdo:name ?oldname
  }
}
INSERT {
  GRAPH hal:CollectionsAndResources {
    <${rdfSubject}> sdo:name "${newName}"
  }
}
WHERE {
  GRAPH hal:CollectionsAndResources {
    <${rdfSubject}> a hal:Annotation .
    OPTIONAL { <${rdfSubject}> sdo:name ?oldname }
  }
}`;

  return executeSparqlQuery(sparqlQuery, token, true)
    .catch(error => {
      console.error('Error setting annotation label:', error);
    });
}

// Function to get the annotation label
export function getAnnotationLabel(rdfSubject) {
  const token = _token;

  const sparqlQuery = `
PREFIX sdo: <https://schema.org/>
PREFIX hal: <https://halcyon.is/ns/>
SELECT ?name WHERE {
  GRAPH hal:CollectionsAndResources {
    <${rdfSubject}> sdo:name ?name
  }
}`;

  return executeSparqlQuery(sparqlQuery, token)
    .then(result => {
      let data;
      try {
        data = JSON.parse(result);
      } catch (error) {
        console.error('Error parsing JSON:', error);
        throw new Error('Failed to parse SPARQL result as JSON.');
      }

      const bindings = data.results.bindings;
      if (bindings.length > 0 && bindings[0].name) {
        return bindings[0].name.value;
      } else {
        return null;
      }
    })
    .catch(error => {
      console.error('Error retrieving annotation label:', error);
      throw error;
    });
}

// This scenario shouldn't happen; handling just in case:
function getCredentials() {
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.style.cssText = [
      'position:fixed', 'inset:0', 'background:rgba(0,0,0,0.5)',
      'z-index:10000', 'display:flex', 'align-items:center', 'justify-content:center'
    ].join(';');

    const modal = document.createElement('div');
    modal.style.cssText = [
      'background:#fff', 'padding:24px', 'border-radius:6px',
      'display:flex', 'flex-direction:column', 'gap:12px',
      'min-width:300px', 'box-shadow:0 4px 24px rgba(0,0,0,0.3)'
    ].join(';');

    const heading = document.createElement('h3');
    heading.textContent = 'Sign In';
    heading.style.cssText = 'margin:0 0 4px 0;font-size:16px;';

    const inputStyle = 'padding:8px;border:1px solid #ccc;border-radius:4px;font-size:14px;';

    const usernameInput = document.createElement('input');
    usernameInput.type = 'text';
    usernameInput.placeholder = 'Username';
    usernameInput.autocomplete = 'username';
    usernameInput.style.cssText = inputStyle;

    const passwordInput = document.createElement('input');
    passwordInput.type = 'password';
    passwordInput.placeholder = 'Password';
    passwordInput.autocomplete = 'current-password';
    passwordInput.style.cssText = inputStyle;

    const btnRow = document.createElement('div');
    btnRow.style.cssText = 'display:flex;gap:8px;justify-content:flex-end;margin-top:4px;';

    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.textContent = 'Cancel';
    cancelBtn.style.cssText = 'padding:8px 16px;border:1px solid #ccc;background:#fff;border-radius:4px;cursor:pointer;font-size:14px;';

    const submitBtn = document.createElement('button');
    submitBtn.type = 'button';
    submitBtn.textContent = 'Sign In';
    submitBtn.style.cssText = 'padding:8px 16px;background:#0066cc;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:14px;';

    function dismiss() {
      document.body.removeChild(overlay);
      resolve({ username: null, password: null });
    }

    function submit() {
      const username = usernameInput.value.trim();
      const password = passwordInput.value;
      if (!username || !password) return;
      document.body.removeChild(overlay);
      resolve({ username, password });
    }

    cancelBtn.addEventListener('click', dismiss);
    submitBtn.addEventListener('click', submit);
    overlay.addEventListener('click', (e) => { if (e.target === overlay) dismiss(); });
    modal.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') submit();
      if (e.key === 'Escape') dismiss();
    });

    btnRow.append(cancelBtn, submitBtn);
    modal.append(heading, usernameInput, passwordInput, btnRow);
    overlay.appendChild(modal);
    document.body.appendChild(overlay);
    usernameInput.focus();
  });
}

async function getToken() {
  const { username, password } = await getCredentials();

  if (!username || !password) return null;

  const authEndpoint = `${window.location.origin}/auth/realms/Halcyon/protocol/openid-connect/token`;
  const authData = new URLSearchParams({
    client_id: 'account',
    username: username,
    password: password,
    grant_type: 'password'
  });

  try {
    const response = await fetch(authEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: authData
    });

    if (response.ok) {
      const data = await response.json();

      // Store the token in module scope only — not on window.
      _token = data.access_token;

      return data.access_token;
    } else {
      const errorText = await response.text();
      console.error('Error fetching token:', response.status, response.statusText, errorText);
    }
  } catch (error) {
    console.error('Fetch error:', error);
  }

  return null;
}
