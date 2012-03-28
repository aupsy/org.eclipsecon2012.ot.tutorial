package antidemo;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import base org.eclipse.jdt.core.JavaConventions;

public team class AntiDemoTeam {

	protected class JC playedBy JavaConventions {

		

		IStatus validateJavaTypeName(String name)
		<- replace
		IStatus validateJavaTypeName(String name, String sourceLevel,
				String complianceLevel);

		static callin IStatus validateJavaTypeName(String name) {
			if (name.startsWith("Foo"))
				return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Don't foo around");
			return base.validateJavaTypeName(name);
		}
		
	}

}
