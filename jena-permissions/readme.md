## Overview
JenaPermissions is a SecurityEvaluator interface and a set of dynamic proxies that apply that interface to Jena Graphs, Models, and associated methods and classes.

The SecurityEvaluator class must be implemented.  This class provides the interface to the authentication results (e.g. getPrincipal())) and the authorization system.

## Programatic usage

Create a SecuredGraph by calling `Factory.getInstance( SecurityEvaluator, String, Graph );`
Create a SecuredModel by calling `Factory.getInstance( SecurityEvaluator, String, Model )` or `ModelFactory.createModelForGraph( SecuredGraph );`

**NOTE:** when creating a model by wrapping a secured graph (e.g. `ModelFactory.createModelForGraph( SecuredGraph );`) the resulting Model does not 
have the same permission requirements that the standard secured model does. 

For instance when creating a list on a secured model calling `model.createList( RDFNode[] );` The standard secured model verifies that the user
has the right to update the triples and allows or denies the entire operation accordingly.  The wrapped secured graph does not have visibility
to the `createList()` command and can only operate on the instructions issued by the `model.createList()` implementation.  In the standard implementation
the model requests the graph to delete one triple and then insert another.  Thus the user must have delete and add permissions, not the update permission.

There are several other cases where the difference in the layer can trip up the permission system.  In all known cases the result is a tighter 
permission definition than was requested.  For simplicity sake we recommend that the wrapped secured graph only be used in cases where access to the
graph as a whole is granted/denied.  In these cases the user either has all CRUD capabilities or none.

## Assembler example
 
    [] a ja:Model ;
       sec:baseModel jena:model ;
       ja:modelName "modelName";
       sec:evaluatorFactory "javaclass";
       .
   
 * jena:model  A model defined in the assembler file.
 * "modelName" The name of the model as identified in the security manager
 * "javaclass" The name of a java class that implements a Evaluator Factory.  The Factory must have static method `getInstance()` that
returns a SecurityEvaluator.


---

## Halcyon fork

This module is a fork of `org.apache.jena:jena-permissions` taken at the
`jena-5.6.0` tag. Upstream deprecated the module and removed it in Jena 6.x;
the Halcyon project maintains it from here (Maven coordinates
`com.ebremer.HalcyonProject:jena-permissions`) for use with Jena 6.1.0+.
Java package namespaces are unchanged (`org.apache.jena.permissions`), the
code remains Apache-2.0 licensed. The upstream TDB1-backed tests were
removed: they exercised non-transactional TDB graph access, a mode that no
longer exists (TDB1 is gone in Jena 6.x and TDB2 requires transactions); the
same graph contracts run against the in-memory graph, and DataSetTest now
uses a plain in-memory dataset.
