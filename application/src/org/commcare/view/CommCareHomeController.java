/**
 * 
 */
package org.commcare.view;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;

import org.commcare.api.transitions.CommCareHomeTransitions;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCareContext;
import org.commcare.util.CommCareSessionController;
import org.javarosa.j2me.log.CrashHandler;
import org.javarosa.j2me.log.HandledCommandListener;
import org.javarosa.j2me.view.J2MEDisplay;


/**
 * @author ctsims
 *
 */
public class CommCareHomeController implements HandledCommandListener {
	CommCareHomeTransitions transitions;
	CommCareHomeScreen view;
	Profile profile;
	CommCareSessionController session;
	
	Vector<Suite> suites;
	boolean admin;
	
	public CommCareHomeController (Vector<Suite> suites, Profile profile, CommCareSessionController session) {
		this.suites = suites;
		this.profile = profile;
		this.session = session;
		admin = CommCareContext._().getUser().isAdminUser();
	}
	
	public void setTransitions (CommCareHomeTransitions transitions) {
		this.transitions = transitions;
	}

	public void start() {
		view = new CommCareHomeScreen(this, suites, admin, profile.isFeatureActive(Profile.FEATURE_REVIEW));
		session.populateMenu(view, "root");
		view.init();
		J2MEDisplay.setView(view);
	}

	public void commandAction(Command c, Displayable d) {
		CrashHandler.commandAction(this, c, d);
	}  

	public void _commandAction(Command c, Displayable d) {
		if (c == view.select) {

			if(view.getCurrentItem() == view.sendAllUnsent) {
				transitions.sendAllUnsent();
			} else if (view.getCurrentItem() == view.serverSync) {
				transitions.serverSync();
			} else if(view.getCurrentItem() == view.reviewRecent) {
				transitions.review();
			} else {
				transitions.sessionItemChosen(view.getSelectedIndex()); 
			}
		} else if (c == view.exit) {
			transitions.logout();
		} else if (c == view.admSettings) {
			transitions.settings();
		} else if (c == view.admNewUser) {
			transitions.newUser();
		} else if (c == view.admEditUsers) {		
			transitions.editUsers();
		} else if (c == view.admDownload) {
			transitions.restoreUserData();
		} else if (c == view.admResetDemo) {
			transitions.resetDemo();
		} else if (c == view.admUpgrade) {
			transitions.upgrade();
		} else if (c == view.admRMSDump) {
			transitions.rmsdump();
		} else if (c == view.admViewLogs) {
			transitions.viewLogs();
		} else if (c == view.admGPRSTest) {
			transitions.gprsTest();
		} else if (c == view.adminLogin) {
			transitions.adminLogin();
		}
	}
}
