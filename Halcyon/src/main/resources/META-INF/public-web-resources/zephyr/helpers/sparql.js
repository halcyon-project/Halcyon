// Helper function to execute SPARQL queries
async function executeSparqlQuery(query, isUpdate = false) {
  const endpoint = `${window.location.origin}/rdf`;

  // C5: no bearer token here, and none in the page. The /rdf proxy attaches the
  // signed-in session's token server-side, so this same-origin fetch just needs
  // the session cookie. The token no longer exists in the DOM for an XSS to steal.
  const options = {
    method: 'POST',
    headers: {
      'Content-Type': isUpdate ? 'application/sparql-update' : 'application/sparql-query'
    },
    body: query
  };

  // console.log("SPARQL Query:", query);

  return fetch(endpoint, options)
    .then(response => {
      // console.log("Response Headers:", response.headers);
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

// Escape a value for use inside a double-quoted SPARQL string literal.
function escapeLiteral(value) {
  return String(value)
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n');
}

// Validate a URI for use inside a SPARQL <...> term: whitespace or angle
// brackets would break out of the term (and the whole update with it).
function validateUri(value) {
  const uri = String(value);
  if (/[\s<>"{}|^`\\]/.test(uri)) {
    throw new Error(`Not a legal URI for a SPARQL term: ${uri}`);
  }
  new URL(uri); // throws on anything that isn't an absolute URI
  return uri;
}

// Function to set the annotation label
export function setAnnotationLabel(rdfSubject, newName) {

  let sparqlQuery;
  try {
    const subject = validateUri(rdfSubject);
    const name = escapeLiteral(newName);
    sparqlQuery = `
PREFIX sdo: <https://schema.org/>
PREFIX hal: <https://halcyon.is/ns/>
DELETE {
  GRAPH hal:CollectionsAndResources {
    <${subject}> sdo:name ?oldname
  }
}
INSERT {
  GRAPH hal:CollectionsAndResources {
    <${subject}> sdo:name "${name}"
  }
}
WHERE {
  GRAPH hal:CollectionsAndResources {
    <${subject}> a hal:Annotation .
    OPTIONAL { <${subject}> sdo:name ?oldname }
  }
}`;
  } catch (error) {
    // Mirror the query-failure path: log and resolve (callers never reject).
    console.error('Error setting annotation label:', error);
    return Promise.resolve();
  }

  return executeSparqlQuery(sparqlQuery, true)
    .then(result => {
      console.log('Annotation label set successfully:', newName);
    })
    .catch(error => {
      console.error('Error setting annotation label:', error);
    });
}

// Function to get the annotation label
export function getAnnotationLabel(rdfSubject) {
  const subject = validateUri(rdfSubject); // throws; callers handle per-item

  const sparqlQuery = `
PREFIX sdo: <https://schema.org/>
PREFIX hal: <https://halcyon.is/ns/>
SELECT ?name WHERE {
  GRAPH hal:CollectionsAndResources {
    <${subject}> sdo:name ?name
  }
}`;

  return executeSparqlQuery(sparqlQuery)
    .then(result => {
      // console.log("Raw SPARQL Query Result:", result);
      // Parse the result as JSON
      let data;
      try {
        data = JSON.parse(result);
      } catch (error) {
        console.error('Error parsing JSON:', error);
        throw new Error('Failed to parse SPARQL result as JSON.');
      }

      // Extract the "name" value from the JSON response
      const bindings = data.results.bindings;
      if (bindings.length > 0 && bindings[0].name) {
        const name = bindings[0].name.value;
        console.log('Retrieved annotation label:', name);
        return name;
      } else {
        console.log('No annotation label found.');
        return null;
      }
    })
    .catch(error => {
      console.error('Error retrieving annotation label:', error);
      throw error;
    });
}
