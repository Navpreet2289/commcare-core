/**
 * 
 */
package org.commcare.util;

import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.installers.BasicInstaller;
import org.commcare.resources.model.installers.LocaleFileInstaller;
import org.commcare.resources.model.installers.SuiteInstaller;
import org.commcare.resources.model.installers.XFormInstaller;
import org.commcare.suite.model.Suite;
import org.javarosa.core.api.IModule;
import org.javarosa.core.services.PrototypeManager;
import org.javarosa.core.services.storage.StorageManager;

/**
 * @author ctsims
 *
 */
public class CommCareModule implements IModule {

	/* (non-Javadoc)
	 * @see org.javarosa.core.api.IModule#registerModule()
	 */
	public void registerModule() {
		String[] prototypes = new String[] {BasicInstaller.class.getName(),
										    LocaleFileInstaller.class.getName(),
										    SuiteInstaller.class.getName(),
										    XFormInstaller.class.getName()};
		PrototypeManager.registerPrototypes(prototypes);
		
		StorageManager.registerStorage(ResourceTable.STORAGE_KEY_GLOBAL, Resource.class);
		StorageManager.registerStorage(Suite.STORAGE_KEY, Suite.class);
	}
}
