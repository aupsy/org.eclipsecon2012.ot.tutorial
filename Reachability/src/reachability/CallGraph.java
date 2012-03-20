package reachability;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;

import base org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import base org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
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
		
		// ===== Follows: nested roles implementing the actual call graph: =====
		// (these are nested role so that their callin bindings only fire,
		//  while the enclosing Main team is active)
		
		/**
		 * A node in the call graph.
		 */
		protected class MethodNode playedBy MethodBinding {
			char[] getSelector() -> get char[] selector;
		}
		
		/** Collect all declarations of 'main' methods, to be used as start nodes for traversal. */
		protected class StartNodeDetector playedBy AbstractMethodDeclaration {
	
			MethodNode getNode() -> get MethodBinding binding;

			void resolve(ClassScope upperScope) <- after void resolve(ClassScope upperScope);
	
			private void resolve(ClassScope upperScope) {
				MethodNode node = this.getNode();
				System.out.println("Found method: "+new String(node.getSelector()));
			}			
		}
	}
}
