package reachability;

import java.util.ArrayList;
import java.util.List;

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
		
		/** 
		 * During traversal remember all main methods as start nodes for the reachability analysis
		 */
		public List<MethodNode> startNodes;
		
		/** Intercept JavaBuilder.buildAll() to define the window of activity. */
		void buildAll() <- replace void buildAll();

		callin void buildAll() {
			// start collecting information:
			startNodes = new ArrayList<MethodNode>();
			
			// perform the actual build with this nested team being active:
			this.activate(ALL_THREADS);
			base.buildAll();
			this.deactivate(ALL_THREADS);
			
			// clean-up
			startNodes = null;
		}
		
		// ===== Follows: nested roles implementing the actual call graph: =====
		// (these are nested role so that their callin bindings only fire,
		//  while the enclosing Main team is active)
		
		/**
		 * A node in the call graph.
		 * It records which other nodes are called from here ({@link #callees}).
		 */
		protected class MethodNode playedBy MethodBinding {

			char[] getSelector() -> get char[] selector;

			protected List<MethodNode> callees;
			
			// so called lifting-constructor, invoked when a base (MethodBinding) is wrapped for the first time.
			public MethodNode(MethodBinding methodBinding) {
				this.callees = new ArrayList<MethodNode>();
			}
		}

		/** Collect all declarations of 'main' methods, to be used as start nodes for traversal. */
		protected class StartNodeDetector playedBy AbstractMethodDeclaration {
	
			MethodNode getNode() -> get MethodBinding binding;

			void resolve(ClassScope upperScope) <- after void resolve(ClassScope upperScope);
	
			private void resolve(ClassScope upperScope) {
				MethodNode node = this.getNode();
				if (node != null && CharOperation.equals(node.getSelector(), "main".toCharArray()))
					LifeCycle.this.startNodes.add(node);
				// if more patterns for start methods are known, add them here.
			}			
		}
	}
}
