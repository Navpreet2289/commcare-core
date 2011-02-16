package org.commcare.util;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.commcare.model.PeriodicEvent;
import org.commcare.util.time.TimeMessageEvent;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.formmanager.view.transport.TransportResponseProcessor;
import org.javarosa.services.transport.CommUtil;
import org.javarosa.services.transport.TransportMessage;
import org.javarosa.services.transport.impl.simplehttp.SimpleHttpTransportMessage;
import org.javarosa.user.transport.HttpUserRegistrationTranslator;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.xmlpull.v1.XmlPullParserException;

public class CommCareHQResponder implements TransportResponseProcessor {
	
	String apiLevelGuess;
	
	public CommCareHQResponder(String apiLevelGuess) {
		this.apiLevelGuess = apiLevelGuess;
	}
	
	//TODO: Replace all response semantics with a single unified response system
	
	public String getResponseMessage(TransportMessage message) {
    	String returnstr = "";
    	
    	// Make sure this is a normal HTTP message before trying anything fancy 
    	if(message.isSuccess() && message.getClass() == SimpleHttpTransportMessage.class) {
    		String numForms = "";
    		boolean understoodResponse = true;
    		byte[] response = null;
    		
    		// No class-cast-exception possible since we just checked
    		SimpleHttpTransportMessage msg = (SimpleHttpTransportMessage)message;
    		
    		//Check for date inconsistencies
    		dateInconsistencyHelper(msg.getRequestProperties().getGMTDate());
    		
    		//Check the API response processor for well-formed, expected results.
    		OpenRosaApiResponseProcessor orHandler = new OpenRosaApiResponseProcessor();
    		
    		if(msg.getResponseProperties().getORApiVersion() == null && apiLevelGuess != null) {
    			msg.getResponseProperties().setRequestProperty("X-OpenRosa-Version",apiLevelGuess);
    		}
    		
    		
    		if(orHandler.handlesResponse(msg)) {
    			
    			//For now the failure mode from this point forward will assume that
    			//with the server having and data safely received, that any state
    			//lost is recoverable, so we'll report on any problems, but won't
    			//fail-fast just yet, since the HTTP layer is not the appropriate
    			//place for ensuring transaction security.
    			
    			try{
    				orHandler.processResponse(msg);
    			} catch (InvalidStructureException e) {
    				//XML doesn't match the appropriate structure
					Logger.exception("hq responder [ise]", e);
					return Localization.get("sending.status.didnotunderstand", new String[] {e.getMessage()});
				} catch (IOException e) {
					//Bad stream - RETRY, maybe?
					Logger.exception("hq responder [ioe]", e);
	    			return Localization.get("sending.status.problem.datasafe");
				} catch (UnfullfilledRequirementsException e) {
					//Misreported version somewhere, device doesn't know how to handle response
					Logger.exception("hq responder [ure]", e);
					return Localization.get("sending.status.didnotunderstand", new String[] {e.getMessage()});
				} catch (XmlPullParserException e) {
					//Bad XML
					Logger.exception("hq responder [xppe]", e);
	    			return Localization.get("sending.status.problem.datasafe");
				}
    		} 
    		
    		//If the response is something custom and unexpected, go through
    		//the old response formats.
    		
			response = msg.getResponseBody();    			
			Document doc = CommUtil.getXMLResponse(response);

			//1.0-ish, but not properly declared (and can't handle transactions)
    		if(doc != null && "OpenRosaResponse".equals(doc.getRootElement().getName()) && HttpUserRegistrationTranslator.XMLNS_ORR.equals(doc.getRootElement().getNamespace())) {
    			//Only relevant (for now!) for Form Submissions
    			try{
    				Element e = doc.getRootElement().getElement(HttpUserRegistrationTranslator.XMLNS_ORR,"message");
    				String responseText = e.getText(0);
    				return responseText;
    			} catch(Exception e) {
    				//No response message
    	    		if( msg.getResponseCode() == 202 ) {
    	    			return Localization.get("sending.status.problem.datasafe");
    	    		} else if(msg.getResponseCode() >= 200 && msg.getResponseCode() < 300) {
    	    			return Localization.get("sending.status.success");
    	    		} else {
    	    			return "";
    	    		}
    			}
    		}
    		
    		//Old (pre 1.0 responder logic)
		 
    		// 200 means everything is cool. 202 means data safe, but a problem
    		if( msg.getResponseCode() == 200 ) {
    			
    			if (doc != null) {
    				Element e = doc.getRootElement();
	    			for (int i = 0; i < e.getChildCount(); i++) {
	    				if (e.getType(i) == Element.ELEMENT) {
	    					Element child = e.getElement(i);
	    					if(child.getName().equals("FormsSubmittedToday")) {
	    						numForms = child.getText(0);
	    						System.out.println("Found it! numforms:"+numForms);
	    						break;
	    					}
	    				}
	    			}
    			} else {
    				understoodResponse = false;
    			}
    		}
    		
    		if (!understoodResponse) {
    			returnstr = Localization.get("sending.status.didnotunderstand",
    					new String[] {response != null ? CommUtil.getString(response) : "[none]"});
    		} else if (numForms.equals(""))
    			returnstr = Localization.get("sending.status.problem.datasafe");
    		else
    			returnstr = Localization.get("sending.status.success", new String[]{numForms});	
    	}
    	
    	return returnstr;
	}
		
	
	private void dateInconsistencyHelper(long date) {
		//Don't do anything if we didn't get back a useful date.
		try {
			if(date == 0) {
				return;
			} else {
				//Get the date into the phone's default timezone
				Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
				c.setTime(new Date(date));
				c.setTimeZone(TimeZone.getDefault());
				
				long difference = Math.abs(c.getTime().getTime() - new Date().getTime());
				
				//if(difference > DateUtils.DAY_IN_MS * 1.5) {
				if(difference > 0) {
					PeriodicEvent.schedule(new TimeMessageEvent());
				}
			}
		}
		catch(Exception e) {
			//This is purely helper code. Don't want it to even crash the system
			Logger.exception("While checking dates", e);
		}
	}
    
}
