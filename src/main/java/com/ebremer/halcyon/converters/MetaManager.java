package com.ebremer.halcyon.converters;

import com.ebremer.halcyon.fea.SHACL;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;

/**
 *
 * @author erich
 */
public class MetaManager {
    
    private Model CreateAction;
    private Model ROCrate;
    private Model meta = ModelFactory.createDefaultModel();
    //private final Graph shapesGraph;
    private final Shapes shapes;
    
    public MetaManager() throws BadMetaFile {
        //try {
          //  RDFDataMgr.read(meta, new FileInputStream("meta.ttl"), Lang.TTL);
        //} catch (FileNotFoundException ex) {
          //  Logger.getLogger(ConvertNuclearSegmentation.class.getName()).log(Level.SEVERE, null, ex);
        //}
        //shapesGraph = RDFDataMgr.loadGraph("shapes.ttl");
        shapes = Shapes.parse(SHACL.getGraph11());
        //InitCA();
        //InitROC();
        System.out.println("Checking CreateAction...");
        ValidationReport report = ShaclValidator.get().validate(shapes, CreateAction.getGraph());
        ShLib.printReport(report);
        boolean valid = report.conforms();
        System.out.println("Checking ROC...");
        report = ShaclValidator.get().validate(shapes, ROCrate.getGraph());
        valid = valid && report.conforms();
        ShLib.printReport(report);
        if (!valid) {
            throw new BadMetaFile("Bad ROCrate");
        }
    }
    
    public MetaManager(Model raw) throws BadMetaFile {
        meta = raw;
        //shapesGraph = RDFDataMgr.loadGraph("shapes.ttl");
        shapes = Shapes.parse(SHACL.getGraph11());
        //InitCA();
        //InitROC();
        System.out.println("Checking CreateAction...");
        ValidationReport report = ShaclValidator.get().validate(shapes, CreateAction.getGraph());
        ShLib.printReport(report);
        boolean valid = report.conforms();
        System.out.println("Checking ROC...");
        report = ShaclValidator.get().validate(shapes, ROCrate.getGraph());
        valid = valid && report.conforms();
        ShLib.printReport(report);
        if (!valid) {
            throw new BadMetaFile("Bad ROCrate");
        }
    }
    
    public Model getCreateAction2() {
        return CreateAction;
    }

    public Model getROCrate() {
        return ROCrate;
    }

    public Model getCreateAction(String iri) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("construct {?neo ?p ?o} where {<urn:hal:CreateActionTemplate> ?p ?o}");
        pss.setIri("neo", iri);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), CreateAction);
        return qe.execConstruct();   
    }

    public Model getROCrate(String iri) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("construct {?neo ?p ?o} where {<urn:hal:DataSetTemplate> ?p ?o}");
        pss.setIri("neo", iri);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ROCrate);
        return qe.execConstruct();  
    }

    /*
    private void InitCA2() {
        QueryExecution qe = QueryExecutionFactory.create("construct {<urn:hal:CreateActionTemplate> ?p ?o} where {<urn:hal:CreateActionTemplate> ?p ?o}", meta);
        CreateAction = qe.execConstruct();
        CreateAction.add(CreateAction.createResource("urn:hal:CreateActionTemplate"),RDF.type, SchemaDO.CreateAction);
    }
    
    private void InitROC2() {
        QueryExecution qe = QueryExecutionFactory.create("construct {<urn:hal:DataSetTemplate> ?p ?o} where {<urn:hal:DataSetTemplate> ?p ?o}", meta);
        ROCrate = qe.execConstruct();
        ROCrate.add(ROCrate.createResource("urn:hal:DataSetTemplate"),RDF.type, SchemaDO.Dataset);
    }
*/
    public static void main(String[] args) throws BadMetaFile {
        loci.common.DebugTools.setRootLevel("ERROR");
        MetaManager mm = new MetaManager();
        //RDFDataMgr.write(System.out, mm.getCreateAction(mm.getCreateAction().createResource("http://www.ebremer.com/CA").getURI()), Lang.TURTLE);
        //RDFDataMgr.write(System.out, mm.getCreateAction(mm.getROCrate().createResource("http://www.ebremer.com/DS").getURI()), Lang.TURTLE);
    }
}