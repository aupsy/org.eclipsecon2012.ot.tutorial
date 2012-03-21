package reachability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.flow.FlowContext;
import org.eclipse.jdt.internal.compiler.flow.FlowInfo;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.core.builder.AbstractImageBuilder;

import base org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import base org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import base org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
import base org.eclipse.jdt.internal.compiler.ast.MessageSend;
import base org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import base org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import base org.eclipse.jdt.internal.compiler.lookup.MethodVerifier;
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
		
		IProject getCurrentProject() -> get IProject currentProject;
		/** Intercept JavaBuilder.buildAll() to define the window of activity. */
		void buildAll() <- replace void buildAll();

		callin void buildAll() {
			// start collecting information:
			startNodes = new ArrayList<MethodNode>();
			clearMarkers();
			
			// perform the actual build with this nested team being active:
			this.activate(ALL_THREADS);
			base.buildAll();
			this.deactivate(ALL_THREADS);
			
			// collect a set of all MethodNodes created during the build:
			Set<MethodNode> allMethods = new HashSet<MethodNode>(); 
			for (MethodNode node : this.getAllRoles(MethodNode.class))
				allMethods.add(node);

			// remove all methods reachable from any start node:
			for (MethodNode start : this.startNodes) {
				start.removeRecursivelyFrom(allMethods);
			}

			// print remaining nodes to std-out:
			System.out.println("vvvv UNREACHABLE METHODS vvvv");
			for(MethodNode node : allMethods) {
				if (!node.isBinary()) {
					System.out.println(node.className()+'\t'+node);
					createMarker(node);
				}
			}
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

			char[][] qualifiedClassName() -> get ReferenceBinding declaringClass
				with { result <- declaringClass.compoundName }

			TypeBinding[] getParameters() -> get TypeBinding[] parameters;

			// retrieve "binary" property using an indirection via the declaring class:
			public boolean isBinary() -> get ReferenceBinding declaringClass
				with { result <- declaringClass.isBinaryBinding() }
				
			
			protected List<MethodNode> callees;
			protected List<MethodNode> overrides;

			// so called lifting-constructor, invoked when a base (MethodBinding) is wrapped for the first time.
			public MethodNode(MethodBinding methodBinding) {
				this.callees = new ArrayList<MethodNode>();
				this.overrides = new ArrayList<MethodNode>();
			}

			/** Remove this node and all transitively reachable nodes from 'allMethods'. */
			public void removeRecursivelyFrom(Set<MethodNode> allMethods) {
				if (!allMethods.contains(this))
					return;
				allMethods.remove(this);
				// remove all methods that are reachable from the current method:
				for(MethodNode node : this.callees)
					node.removeRecursivelyFrom(allMethods);
				// remove all methods that override the current method:
				for(MethodNode node : this.overrides)
					node.removeRecursivelyFrom(allMethods);
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

		/** Generalize commonality of all call edges: connect a method to one callee. */
		protected abstract class CallEdge {
			abstract MethodNode getTarget();
			
			protected void analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo) {
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

		/** Let MessageSend play the role CallEdge. */
		protected class MessageCallEdge extends CallEdge playedBy MessageSend {

			MethodNode getTarget() -> get MethodBinding binding;
	
			void analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo)
			<- after
			FlowInfo analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo);		
		}

		/** Let AllocationExpression play the role CallEdge. */
		protected class CtorCallEdge extends CallEdge playedBy AllocationExpression {

			MethodNode getTarget() -> get MethodBinding binding;
	
			void analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo)
			<- after
			FlowInfo analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo);		
		}

		/** Let ExplicitConstructorCall play the role CallEdge. */
		protected class SelfCallEdge extends CallEdge playedBy ExplicitConstructorCall {

			MethodNode getTarget() -> get MethodBinding binding;
	
			void analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo)
			<- after
			FlowInfo analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo);		
		}

		/** In the context of a method call we need to find the enclosing (caller) method. */
		protected class BlockScope playedBy BlockScope {
			MethodNode getCaller() -> MethodScope methodScope() 
				with { result <- result.referenceMethodBinding() }		
		}

		/** Connect any super methods to their overrides. */
		protected class Inheritance playedBy MethodVerifier {
		
			checkAgainstInheritedMethods <- after checkAgainstInheritedMethods;
		
			private void checkAgainstInheritedMethods(MethodNode currentMethod, MethodNode[] methods) {
				for (MethodNode superMethod : methods)
					if (superMethod != null)
						superMethod.overrides.add(currentMethod);
			}
		}
		public void clearMarkers() {
			try {
				getCurrentProject().deleteMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
			} catch (CoreException e) { /* ignore */ }
		}
		public void createMarker(MethodNode method) {
			try {
				// find the IType for the declaring class:
				IJavaProject javaProject = JavaCore.create(getCurrentProject());
				char[] qualifiedClassName = Signature.toQualifiedName(method.qualifiedClassName());
				IType declaringType = javaProject.findType(String.valueOf(qualifiedClassName));

				// find the IMethod:
				TypeBinding[] parameters = method.getParameters();
				String[] paramTypes = new String[parameters.length];
				for (int i = 0; i <	parameters.length; i++) {
					paramTypes[i] = Signature.createTypeSignature(parameters[i].sourceName(), false);
				}
				IMethod iMethod = declaringType.getMethod(String.valueOf(method.getSelector()), paramTypes); 
				if (!iMethod.exists() || !(iMethod instanceof ISourceReference))
					return;
				
				ISourceRange nameRange = ((ISourceReference)iMethod).getNameRange();
				
				// create and configure a new marker:
				IMarker marker = declaringType.getResource().createMarker(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);

		    	String[] allNames = AbstractImageBuilder.JAVA_PROBLEM_MARKER_ATTRIBUTE_NAMES;
		    	Object[] allValues = new Object[allNames.length];
				// standard attributes
				int index = 0;
				allValues[index++] = "Unreachable method "+String.valueOf(method.getSelector()); // message
				allValues[index++] = IMarker.SEVERITY_WARNING; // severity
				allValues[index++] = new Integer(IProblem.UnusedPrivateMethod); // ID
				allValues[index++] = nameRange.getOffset(); // start
				allValues[index++] = nameRange.getOffset() + nameRange.getLength(); // end
				allValues[index++] = null; // line (unknown)
				allValues[index++] = "0:"; // arguments
				allValues[index++] = new Integer(CategorizedProblem.CAT_INTERNAL); // category ID

				marker.setAttributes(allNames, allValues);

			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CoreException e) {
				// ignore
			}
		}
	}
}
