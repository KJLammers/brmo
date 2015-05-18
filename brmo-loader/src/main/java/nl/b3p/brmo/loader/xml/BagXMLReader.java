/*
 * Copyright (C) 2015 B3Partners B.V.
 */
package nl.b3p.brmo.loader.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;
import nl.b3p.brmo.loader.entity.BagBericht;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * reader of BAG mutatie files
 *
 * @author Boy de Wit
 * @author Mark Prins <mark@b3partners.nl>
 */
public class BagXMLReader extends BrmoXMLReader {

    private static final Log log = LogFactory.getLog(BagXMLReader.class);

    private final XMLInputFactory factory = XMLInputFactory.newInstance();
    private final XMLStreamReader streamReader;
    private final Transformer transformer;
    private final DocumentBuilder builder;
    private final XMLOutputFactory xmlof;

    // soort laadproces
    private String soort = "bag";

    public static final String MUTATIE_PRODUCT = "Mutatie-product";
    public static final String LVC_PRODUCT = "LVC-product";
    private static final String DATUM_TIJD_LV = "datumtijdstempelLV";
    private static final String STAND_PEILDATUM = "StandPeildatum";
    private static final String STAND_TECHNISCHEDATUM = "StandTechnischeDatum";

    private BagBericht nextBericht = null;

    public static final Map<String,String> lvcProductToObjectType;

    static {
        Map<String,String> m = new HashMap();
        m.put("Standplaats", "STA");
        m.put("Ligplaats", "LIG");
        m.put("Nummeraanduiding", "NUM");
        m.put("OpenbareRuimte", "OPR");
        m.put("Pand", "PND");
        m.put("Verblijfsobject", "VBO");
        m.put("Standplaats", "STA");
        m.put("Woonplaats", "WPL");
        lvcProductToObjectType = Collections.unmodifiableMap(m);
    }

    public BagXMLReader(InputStream in)
            throws XMLStreamException,
            TransformerConfigurationException,
            ParserConfigurationException {

        streamReader = factory.createXMLStreamReader(in);

        TransformerFactory tf = TransformerFactory.newInstance();
        transformer = tf.newTransformer();

        // Vanwege splitsing is repairen van namespaces nodig, anders ontbreekt
        // de namespace prefix declaratie xmlns:xlink="http://www.w3.org/1999/xlink"
        // in de output en mislukt de XSL transformatie
        xmlof = XMLOutputFactory.newInstance();
        xmlof.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);

        DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
        dbfactory.setNamespaceAware(true);
        dbfactory.setNamespaceAware(true);
        builder = dbfactory.newDocumentBuilder();

        init();
    }

    @Override
    public void init() {
        String technischeDatum = null;
        String peilDatum = null;
        try {
            while (streamReader.hasNext()) {
                if (streamReader.isStartElement()) {
                    String localName = streamReader.getLocalName();

                    if (localName.equals(MUTATIE_PRODUCT) || localName.equals(LVC_PRODUCT)) {
                        break;
                    }

                    if (localName.equals(DATUM_TIJD_LV)) {
                        setDatumAsString(streamReader.getElementText());
                    }
                    if(localName.equals(STAND_TECHNISCHEDATUM)) {
                        technischeDatum = streamReader.getElementText();
                    }
                    if(localName.equals(STAND_PEILDATUM)) {
                        peilDatum = streamReader.getElementText();
                    }
                }
                streamReader.next();
            }
        } catch (XMLStreamException ex) {
            log.error("Error while streaming XML", ex);
        }
        if(peilDatum != null) {
            setDatumAsString(peilDatum, "yyyyMMdd");
        } else if(technischeDatum != null) {
            setDatumAsString(technischeDatum, "yyyyMMdd");
        } else {
            setBestandsDatum(new Date());
        }
    }

    @Override
    public boolean hasNext() throws SAXException, IOException, TransformerException {
        if (nextBericht != null) {
            return true;
        }
        try {
            while (streamReader.hasNext()) {
                if (streamReader.isStartElement() && streamReader.getLocalName().equals(MUTATIE_PRODUCT)) {
                    // parse en check voor "Nieuw" element en niet "Origineel"/"Wijziging"
                    StringWriter sw = new StringWriter();

                    // Hiermee wordt de StreamReader het volgende product geforward
                    transformer.transform(new StAXSource(streamReader), new StAXResult(xmlof.createXMLStreamWriter(sw)));

                    Document d = builder.parse(new InputSource(new StringReader(sw.toString())));
                    NodeList children = d.getDocumentElement().getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if ("Nieuw".equals(child.getLocalName())) {
                            // Mutatie-product met Nieuw als child element gevonden
                            // Origineel/Wijziging kunnen overgeslagen worden omdat er altijd een Nieuw
                            // element voor hetzelfde object is. In de BRMO wordt de historie zelf
                            // bijgehouden dus zijn de wijzigingen niet interessant
                            nextBericht = new BagBericht(sw.toString(), d);
                            return true;
                        }
                    }
                } else if(streamReader.isStartElement() && lvcProductToObjectType.containsKey(streamReader.getLocalName())) {
                    StringWriter sw = new StringWriter();
                    // Hiermee wordt de StreamReader het volgende product geforward
                    transformer.transform(new StAXSource(streamReader), new StAXResult(xmlof.createXMLStreamWriter(sw)));
                    Document d = builder.parse(new InputSource(new StringReader(sw.toString())));

                    nextBericht = new BagBericht(sw.toString(), d);

                    // Bij een levering zit er geen datum in een "Verwerking" element,
                    // pak peildatum van stand
                    nextBericht.setDatum(getBestandsDatum());
                    return true;
                } else {
                    streamReader.nextTag();
                }
            }
        } catch (XMLStreamException ex) {
            log.error("Error while streaming XML", ex);
        }
        return false;
    }

    @Override
    public BagBericht next() throws TransformerException, XMLStreamException {
        BagBericht b = nextBericht;
        nextBericht = null;
        return b;
    }
}