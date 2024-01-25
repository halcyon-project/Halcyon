package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import static org.apache.commons.codec.binary.Base64.encodeBase64;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.RDFWriterBuilder;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author erich
 */
public class XMP {
    private BigDecimal magnification = null;
    private BigDecimal ppsx = null;
    private BigDecimal ppsy = null;
    private final String uuid;
    private String manufacturer = null;
    private String manufacturerdevicename = null;
    private byte[] iccprofile = null;
    private String ImageComments = null;
    private BigDecimal exposuretime = null;
    
    public XMP() {
        this.uuid = "https://dummyabcde.com/"+UUID.randomUUID().toString();
    }
    
    public String getUUID() {
        return uuid;
    }
    
    public void setMagnification(BigDecimal magnification) {        
        this.magnification = magnification;
    }
    
    public void setExposureTime(BigDecimal s) {        
        this.exposuretime = s;
    }
    
    public void setManufacturer(String s) {        
        this.manufacturer = s;
    }

    public void setImageComments(String s) {        
        this.ImageComments = s;
    }
    
    public void setICCColorProfile(byte[] s) {        
        this.iccprofile = s;
    }
        
    public void setManufacturerDeviceName(String s) {        
        this.manufacturerdevicename = s;
    }
    
    public void setSizePerPixelXinMM(BigDecimal ppsx) {
        this.ppsx = ppsx;
    }
    
    public void setSizePerPixelYinMM(BigDecimal ppsy) {
        this.ppsy = ppsy;
    }
    
    public byte[] getXMP() {
        Model m = ModelFactory.createDefaultModel();
        Resource root = m.createResource(uuid);
        if (magnification!=null) {
            DecimalFormat f = new DecimalFormat("#.##############################");
            f.setDecimalSeparatorAlwaysShown(false);
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ObjectiveLensPower"), f.format(magnification));
        }
        if (manufacturer!=null) {
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/Manufacturer"), manufacturer);
        }
        if (manufacturerdevicename!=null) {
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ManufacturerModelName"), manufacturerdevicename);
        }
        if (iccprofile!=null) {
            Literal lit = m.createTypedLiteral(encodeBase64(iccprofile), XSDDatatype.XSDbase64Binary);
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ICCProfile"), lit);
        }
        if (ImageComments!=null) {
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ImageComments"), ImageComments);
        }
        if (exposuretime!=null) {
            DecimalFormat f = new DecimalFormat("#.##############################");
            f.setDecimalSeparatorAlwaysShown(false);
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ExposureTime"), m.createLiteral(f.format(exposuretime)));
        }        
        if ((ppsx!=null)&&(ppsy!=null)) {
            ArrayList<RDFNode> list = new ArrayList<>(); 
            DecimalFormat f = new DecimalFormat("#.##############################");
            f.setDecimalSeparatorAlwaysShown(false);      
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/PixelSpacing"), m.createList(m.createLiteral(f.format(ppsy)), m.createLiteral(f.format(ppsx))));
        }
        m.setNsPrefix("DICOM", "http://ns.adobe.com/DICOM/");
        m.setNsPrefix("rdf", RDF.uri);
        m.setNsPrefix("xmpMM", "http://ns.adobe.com/xap/1.0/mm/");
        m.setNsPrefix("xmp", "http://ns.adobe.com/xap/1.0/");
        m.setNsPrefix("xsd", XSD.NS);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        RDFWriterBuilder builder = RDFWriterBuilder.create();
         builder
            .source(m)
            .lang(Lang.RDFXML)
            .base(uuid)
            .output(os);
        builder.build();
        return os.toByteArray();
    }
    
    public String getXMPString() {
        String packet = new String(getXMP(),StandardCharsets.UTF_8);
        packet = "<?xpacket begin='﻿\uFEFF' id='W5M0MpCehiHzreSzNTczkc9d'?>\n<x:xmpmeta xmlns:x='adobe:ns:meta/' x:xmptk='"+HalcyonSettings.HALCYONSOFTWARE+"'>\n"+packet;
        packet = packet+"</x:xmpmeta>\n"+(new String(new char[2424]).replace('\0', ' '))+"\n<?xpacket end='w'?>";
        return packet;
    }
    
    public static Model getXMP(String base, String xml) {
        InputStream xmlis = new ByteArrayInputStream(xml.getBytes());
        Model xmp = ModelFactory.createDefaultModel();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlis);
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    if (prefix.equals("rdf")) {
                        return RDF.getURI();
                    }
                    return null;
                }
                @Override
                public Iterator getPrefixes(String val) { return null; }
                @Override
                public String getPrefix(String uri) { return null; }
            });            
            String expression = "//rdf:RDF"; // XPath expression to find the rdf:RDF element
            XPathExpression expr = xpath.compile(expression);
            Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);
            if (node != null) {
                //System.out.println("Found node: " + node.getNodeName());
                StringWriter writer = new StringWriter();
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.transform(new DOMSource(node), new StreamResult(writer));                
                //System.out.println(writer.toString());
                byte[] byteArray = writer.toString().getBytes(StandardCharsets.UTF_8);
                RDFParserBuilder.create()
                    .base(base)
                    .source(new ByteArrayInputStream(byteArray))
                    .lang(Lang.RDFXML)
                    .parse(xmp);                   
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return xmp;
    }
    
    public static InputStream grab() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse("xmp.xml");
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    if (prefix.equals("rdf")) {
                        return RDF.getURI();
                    }
                    return null;
                }
                @Override
                public Iterator getPrefixes(String val) { return null; }
                @Override
                public String getPrefix(String uri) { return null; }
            });            
            String expression = "//rdf:RDF"; // XPath expression to find the rdf:RDF element
            XPathExpression expr = xpath.compile(expression);
            Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);
            if (node != null) {
                System.out.println("Found node: " + node.getNodeName());
                StringWriter writer = new StringWriter();
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.transform(new DOMSource(node), new StreamResult(writer));                
                System.out.println(writer.toString());
                byte[] byteArray = writer.toString().getBytes(StandardCharsets.UTF_8);
                return new ByteArrayInputStream(byteArray);                
            } else {
                System.out.println("Node not found.");
                return new FileInputStream("xmp.xml");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String args[]) throws FileNotFoundException {                 
        System.out.println("YAY !!!!=========================================================================================");
        XMP xmp = new XMP();
        xmp.setMagnification(BigDecimal.valueOf(40.4));
        xmp.setSizePerPixelXinMM(BigDecimal.valueOf(0.2468d).divide(BigDecimal.valueOf(1000000)));
        xmp.setSizePerPixelYinMM(BigDecimal.valueOf(0.2468d).divide(BigDecimal.valueOf(1000000)));
        xmp.setExposureTime(BigDecimal.valueOf(0.0041234d));
        System.out.println(xmp.getXMPString());
    }
}
