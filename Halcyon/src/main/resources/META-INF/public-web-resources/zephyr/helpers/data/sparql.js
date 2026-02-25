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
async function getCredentials() {
  const username = prompt("Please enter your username:");
  const password = prompt("Please enter your password:");

  return { username, password };
}

async function getToken() {
  const { username, password } = await getCredentials();

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
