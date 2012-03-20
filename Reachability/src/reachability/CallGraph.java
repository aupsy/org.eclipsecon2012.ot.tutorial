package reachability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.flow.FlowContext;
import org.eclipse.jdt.internal.compiler.flow.FlowInfo;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;

import base org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import base org.eclipse.jdt.internal.compiler.ast.MessageSend;
import base org.eclipse.jdt.internal.compiler.lookup.BlockScope;
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
			
			// collect a set of all MethodNodes created during the build:
			Set<MethodNode> allMethods = new HashSet<MethodNode>(); 
			for (MethodNode node : this.getAllRoles(MethodNode.class))
				allMethods.add(node);

			// print remaining nodes to std-out:
			System.out.println("vvvv FOUND METHODS vvvv");
			for(MethodNode node : allMethods)
				if (!node.isBinary())
					System.out.println(node.className()+'\t'+node);
			System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\n");

			// clean-up
			startNodes = null;
			CallGraph.this.unregisterRole(this);
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

			String toString() => String toString();
			
			String className() -> get ReferenceBinding declaringClass
				with { result <- String.valueOf(declaringClass.sourceName) }

			// retrieve "binary" property using an indirection via the declaring class:
			public boolean isBinary() -> get ReferenceBinding declaringClass
				with { result <- declaringClass.isBinaryBinding() }
				
			
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

		/** A MessageSend connects a method with a callee. */
		protected class MessageCallEdge playedBy MessageSend {

			MethodNode getTarget() -> get MethodBinding binding;
	
			void analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo)
			<- after
			FlowInfo analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo);		

			void analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo) {
				// ignore message sends in unreachable code:
				if ((flowInfo.tagBits & FlowInfo.UNREACHABLE) != 0)
					return;
				MethodNode callee = getTarget();
				// only consider source methods:
				if (callee == null || callee.isBinary())
					return;
				MethodNode caller = currentScope.getCaller();
				if (caller != null)
					caller.callees.add(callee);
			}
		}

		/** In the context of a method call we need to find the enclosing (caller) method. */
		protected class BlockScope playedBy BlockScope {
			MethodNode getCaller() -> MethodScope methodScope() 
				with { result <- result.referenceMethodBinding() }		
		}
	}
}
