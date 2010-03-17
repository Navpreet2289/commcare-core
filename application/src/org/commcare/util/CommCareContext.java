/**
 * 
 */
package org.commcare.util;

import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.midlet.MIDlet;

import org.commcare.core.properties.CommCareProperties;
import org.javarosa.cases.CaseManagementModule;
import org.javarosa.cases.model.Case;
import org.javarosa.cases.util.CasePreloadHandler;
import org.javarosa.chsreferral.PatientReferralModule;
import org.javarosa.chsreferral.model.PatientReferral;
import org.javarosa.chsreferral.util.PatientReferralPreloader;
import org.javarosa.core.model.CoreModelModule;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.model.utils.IPreloadHandler;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.properties.JavaRosaPropertyRules;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.StorageManager;
import org.javarosa.core.services.transport.payload.IDataPayload;
import org.javarosa.core.util.JavaRosaCoreModule;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.j2me.J2MEModule;
import org.javarosa.j2me.storage.rms.RMSRecordLoc;
import org.javarosa.j2me.storage.rms.RMSStorageUtility;
import org.javarosa.j2me.util.DumpRMS;
import org.javarosa.j2me.view.J2MEDisplay;
import org.javarosa.model.xform.XFormsModule;
import org.javarosa.resources.locale.LanguagePackModule;
import org.javarosa.resources.locale.LanguageUtils;
import org.javarosa.services.transport.TransportManagerModule;
import org.javarosa.services.transport.TransportMessage;
import org.javarosa.services.transport.impl.simplehttp.SimpleHttpTransportMessage;
import org.javarosa.user.activity.UserModule;
import org.javarosa.user.model.User;
import org.javarosa.user.utility.UserUtility;

/**
 * @author ctsims
 *
 */
public class CommCareContext {

	private static CommCareContext i;
	
	private MIDlet midlet;
	private User user;
	
	private CommCareManager manager;
	
	protected boolean inDemoMode;
	
	public TransportMessage buildMessage(IDataPayload payload) {
		//Right now we have to just give the message the stream, rather than the payload,
		//since the transport layer won't take payloads. This should be fixed _as soon 
		//as possible_ so that we don't either (A) blow up the memory or (B) lose the ability
		//to send payloads > than the phones' heap.
		try {
			return new SimpleHttpTransportMessage(payload.getPayloadStream(), PropertyManager._().getSingularProperty(CommCareProperties.POST_URL_PROPERTY));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Error Serializing Data to be transported");
		}
	}
	
	public MIDlet getMidlet() {
		return midlet;
	}
	
	public CommCareManager getManager() {
		return manager;
	}
	
	public void configureApp(MIDlet m) {
		failsafeInit(m);
		Logger.log("app-start", "");
		
		this.midlet = m;
		J2MEDisplay.init(m);
		loadModules();
		setProperties();
		
		registerAddtlStorage();
		StorageManager.repairAll();
		
		manager = new CommCareManager();
		manager.init(CommCareUtil.getProfileReference());
		
		UserUtility.populateAdminUser();
		inDemoMode = false;
		
		purgeScheduler();
		
		//When we might initailzie language files, we need to make sure it's not trying
		//to load any of them into memory, since the default ones are not guaranteed to
		//be added later.
		Localization.setLocale("default");
		manager.initialize();
		
		//Now we can initialize the language for real.
		LanguageUtils.initializeLanguage(true,"default");
	}

	private void failsafeInit (MIDlet m) {
		DumpRMS.RMSRecoveryHook(m);
		new J2MEModule().registerModule();
	}
	
	protected void registerAddtlStorage () {
		//do nothing
	}

	private void loadModules() {
		new JavaRosaCoreModule().registerModule();
		new UserModule().registerModule();
		new LanguagePackModule().registerModule();
		new PatientReferralModule().registerModule();
		new CoreModelModule().registerModule();
		new XFormsModule().registerModule();
		new CaseManagementModule().registerModule();
		new TransportManagerModule().registerModule();
		new CommCareModule().registerModule();
	}
	
	protected void setProperties() {
		PropertyManager._().addRules(new JavaRosaPropertyRules());
		PropertyManager._().addRules(new CommCareProperties());
		PropertyUtils.initializeProperty("DeviceID", PropertyUtils.genGUID(25));
		
		PropertyManager._().setProperty(CommCareProperties.COMMCARE_VERSION, CommCareUtil.getVersion());
	}
	
	public static void init(MIDlet m) {
		i = new CommCareContext();
		i.configureApp(m);
	}
	
	public static CommCareContext _() {
		if(i == null) {
			throw new RuntimeException("CommCareContext must be initialized with the Midlet and Implementation to be used.");
		}
		return i;
	}

	public void setUser (User u) {
		this.user = u;
	}
	
	public User getUser () {
		return user;
	}
	
	
	public Vector<IFunctionHandler> getFuncHandlers () {
		Vector<IFunctionHandler> handlers = new Vector<IFunctionHandler>();
		handlers.addElement(new HouseholdExistsFuncHandler());
		return handlers;
	}
	
	/// Probably put this stuff into app specific ones.
	public Vector<IPreloadHandler> getPreloaders() {
		return getPreloaders(null, null);
	}
	
	public Vector<IPreloadHandler> getPreloaders(PatientReferral r) {
		Case c = CommCareUtil.getCase(r.getLinkedId());
		return getPreloaders(c, r);
	}
	
	public Vector<IPreloadHandler> getPreloaders(Case c) {
		return getPreloaders(c, null);
	}
	
	public Vector<IPreloadHandler> getPreloaders(Case c, PatientReferral r) {
		Vector<IPreloadHandler> handlers = new Vector<IPreloadHandler>();
		if(c != null) {
			CasePreloadHandler p = new CasePreloadHandler(c);
			handlers.addElement(p);
		}
		if(r != null) {
			PatientReferralPreloader rp = new PatientReferralPreloader(r);
			handlers.addElement(rp);
		}
		MetaPreloadHandler meta = new MetaPreloadHandler(this.getUser());
		handlers.addElement(meta);
		return handlers;		
	}
	
	private void registerDemoStorage (String key, Class type) {
		StorageManager.registerStorage(key, "DEMO_" + key, type);
	}
	
	public void toggleDemoMode(boolean demoOn) {
		if (demoOn != inDemoMode) {
			inDemoMode = demoOn;
			if (demoOn) {
				registerDemoStorage(Case.STORAGE_KEY, Case.class);
				registerDemoStorage(PatientReferral.STORAGE_KEY, PatientReferral.class);
				registerDemoStorage(FormInstance.STORAGE_KEY, FormInstance.class);
				//TODO: Use new transport message queue
			} else {
				StorageManager.registerStorage(Case.STORAGE_KEY, Case.class);
				StorageManager.registerStorage(PatientReferral.STORAGE_KEY, PatientReferral.class);
				StorageManager.registerStorage(FormInstance.STORAGE_KEY, FormInstance.class);
				//TODO: Use new transport message queue
			}
		}
	}
	
	public void resetDemoData() {
		//#debug debug
		System.out.println("Resetting demo data");
	
		StorageManager.getStorage(Case.STORAGE_KEY).removeAll();
		StorageManager.getStorage(PatientReferral.STORAGE_KEY).removeAll();
		StorageManager.getStorage(FormInstance.STORAGE_KEY).removeAll();
		//TODO: Use new transport message queue
	}

	public void purgeScheduler () {
		int purgeFreq = CommCareProperties.parsePurgeFreq(PropertyManager._().getSingularProperty(CommCareProperties.PURGE_FREQ));
		Date purgeLast = CommCareProperties.parseLastPurge(PropertyManager._().getSingularProperty(CommCareProperties.PURGE_LAST));
		
		if (purgeFreq <= 0 || purgeLast == null || ((new Date().getTime() - purgeLast.getTime()) / 86400000l) >= purgeFreq) {
			String logMsg = purgeMsg(autoPurge());
			PropertyManager._().setProperty(CommCareProperties.PURGE_LAST, DateUtils.formatDateTime(new Date(), DateUtils.FORMAT_ISO8601));
			Logger.log("record-purge", logMsg);
		}
	}
	
	public Hashtable<String, Hashtable<Integer, String>> autoPurge () {
		Hashtable<String, Hashtable<Integer, String>> deletedLog = new Hashtable<String, Hashtable<Integer, String>>();
		
		//attempt to purge different types of objects in such an order that, if interrupted, we'll avoid referential integrity errors
		
		//1) tx queue is self-managing
		//do nothing
		
		//2) saved forms (keep forms not yet recorded; sent/unsent status should matter in future, but not now, because new tx layer is naive)
		purgeRMS(FormInstance.STORAGE_KEY,
			new EntityFilter<FormInstance> () {
				//EntityFilter<FormInstance> antiFilter = new BracRecentFormFilter();
			
				//do the opposite of the recent form filter; i.e., if form shows up in the 'unrecorded forms' list, it is NOT safe to delete
				public int preFilter (int id, Hashtable metaData) {
					return EntityFilter.PREFILTER_INCLUDE;
					//return antiFilter.preFilter(id, metaData) == EntityFilter.PREFILTER_INCLUDE ?
							//EntityFilter.PREFILTER_EXCLUDE : EntityFilter.PREFILTER_INCLUDE;
				}
			
				public boolean matches(FormInstance sf) {
					return true;
					//return !antiFilter.matches(sf);
				}
			}, deletedLog);
		
		//3) referrals (keep only pending referrals)
		final Vector<String> casesWithActiveReferrals = new Vector<String>();
		purgeRMS(PatientReferral.STORAGE_KEY,
			new EntityFilter<PatientReferral> () {
				public boolean matches(PatientReferral r) {
					if (r.isPending()) {
						String caseID = r.getLinkedId();
						if (!casesWithActiveReferrals.contains(caseID)) {
							casesWithActiveReferrals.addElement(caseID);
						}
						return false;
					} else {
						return true;
					}
				}
			}, deletedLog);
		
		//4) cases (delete cases that are closed AND have no open referrals pointing to them
		//          AND have no unrecorded forms)		
		purgeRMS(Case.STORAGE_KEY,
			new EntityFilter<Case> () {
				public boolean matches(Case c) {
					return c.isClosed() && !casesWithActiveReferrals.contains(c.getCaseId());
				}
			}, deletedLog);

		//5) reclog will never grow that large in size
		//do nothing
		
		//6) incident log is (mostly) self-managing
		//do nothing
		
		return deletedLog;
	}
	
	private void purgeRMS (String key, EntityFilter filt, Hashtable<String, Hashtable<Integer, String>> deletedLog) {
		RMSStorageUtility rms = (RMSStorageUtility)StorageManager.getStorage(key);
		Hashtable<Integer, RMSRecordLoc> index = rms.getIDIndexRecord();
		
		Vector<Integer> deletedIDs = rms.removeAll(filt);
		
		Hashtable<Integer, String> deletedDetail = new Hashtable<Integer, String>();
		for (int i = 0; i < deletedIDs.size(); i++) {
			int id = deletedIDs.elementAt(i).intValue();
			RMSRecordLoc detail = index.get(new Integer(id));
			deletedDetail.put(new Integer(id), detail != null ? "(" + detail.rmsID + "," + detail.recID + ")" : "?");
		}
		deletedLog.put(key, deletedDetail);
	}
	
	//aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
	private String purgeMsg (Hashtable<String, Hashtable<Integer, String>> detail) {
		if (detail == null)
			return "";
		
		StringBuffer sb = new StringBuffer();
		int i = 0;
		for (Enumeration e = detail.keys(); e.hasMoreElements(); i++) {
			String key = (String)e.nextElement();
			Hashtable<Integer, String> rmsDetail = detail.get(key);
			sb.append(key + "[");
			int j = 0;
			for (Enumeration f = rmsDetail.keys(); f.hasMoreElements(); j++) {
				int id = ((Integer)f.nextElement()).intValue();
				String ext = rmsDetail.get(new Integer(id));
				sb.append(id + (ext != null ? ":" + ext : ""));
				if (j < rmsDetail.size() - 1)
					sb.append(",");
			}
			sb.append("]");
			if (i < detail.size() - 1)
				sb.append(",");			
		}
		return sb.toString();
	}
}