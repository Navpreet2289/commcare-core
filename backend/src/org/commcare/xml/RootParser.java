/**
 * 
 */
package org.commcare.xml;

import java.io.IOException;

import org.commcare.xml.util.InvalidStructureException;
import org.javarosa.core.reference.Root;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @author ctsims
 *
 */
public class RootParser extends ElementParser<Root> {

	public RootParser(KXmlParser parser) {
		super(parser);
	}

	/* (non-Javadoc)
	 * @see org.commcare.xml.ElementParser#parse()
	 */
	public Root parse() throws InvalidStructureException, IOException, XmlPullParserException {
		this.checkNode("root");
		
		String id = parser.getAttributeValue(null, "prefix");
		String readonly = parser.getAttributeValue(null, "readonly");
		
		//Get the child or error out if none exists
		getNextTagInBlock("root");
		
		String referenceType = parser.getName().toLowerCase();
		String path = parser.getAttributeValue(null,"path"); 
		if(referenceType.equals("filesystem")) {
			return new Root("jr://" + id + "/","jr://file" + path);
		} else if(referenceType.equals("resource")) {
			return new Root("jr://" + id + "/", "jr://resource" + path);
		} else if(referenceType.equals("absolute")) {
			return new Root("jr://" + id + "/", path);
		}
		else {
			throw new InvalidStructureException("No available reference types to parse out reference root " + referenceType,parser);
		}
	}

}