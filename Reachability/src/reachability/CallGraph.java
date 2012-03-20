package reachability;

import base org.eclipse.jdt.internal.core.builder.JavaBuilder;

/** 
 * This team adapts classes from JDT/Core 
 * to perform reachability analysis during each full build. 
 */
@SuppressWarnings("restriction")
public team class CallGraph {

	/** Nested team that defines the context when our analysis should be active. */
	protected team class LifeCycle playedBy JavaBuilder {

		/** Intercept JavaBuilder.buildAll() to define the window of activity. */
		void buildAll() <- replace void buildAll();

		callin void buildAll() {
			// perform the actual build with this nested team being active:
			this.activate(ALL_THREADS);
			base.buildAll();
			this.deactivate(ALL_THREADS);	
		}
	}
}
