/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.report.resolution;

import java.util.List;
import java.util.Map;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.resource.Resource;

/**
 * A resolution report is associated with a single resolve process.  Entries
 * contained in a resolution report will contain entries for all resources
 * that were attempted to be resolved in a single resolve process.
 * Resolution reports are gathered by a special type of {@link ResolverHook}
 * which implements the report {@link Listener} interface.  The following
 * example demonstrates how to gather a resolution report for a list of bundles
 * <pre>

	public static ResolutionReport getResolutionReport(Bundle[] bundles, BundleContext context) {
		DiagReportListener reportListener = new DiagReportListener(bundles);
		ServiceRegistration<ResolverHookFactory> hookReg = context.registerService(ResolverHookFactory.class, reportListener, null);
		try {
			Bundle systemBundle = context.getBundle(Constants.SYSTEM_BUNDLE_LOCATION);
			FrameworkWiring frameworkWiring = systemBundle.adapt(FrameworkWiring.class);
			frameworkWiring.resolveBundles(Arrays.asList(bundles));
			return reportListener.getReport();
		} finally {
			hookReg.unregister();
		}
	}

	private static class DiagReportListener implements ResolverHookFactory {
		private final Collection<BundleRevision> targetTriggers = new ArrayList<BundleRevision>();
		volatile ResolutionReport report = null;

		public DiagReportListener(Bundle[] bundles) {
			for (Bundle bundle : bundles) {
				BundleRevision revision = bundle.adapt(BundleRevision.class);
				if (revision != null && revision.getWiring() == null) {
					targetTriggers.add(revision);
				}
			}
			
		}

		class DiagResolverHook implements ResolverHook, ResolutionReport.Listener {

			public void handleResolutionReport(ResolutionReport report) {
				DiagReportListener.this.report = report;
			}

			public void filterResolvable(Collection<BundleRevision> candidates) { }

			public void filterSingletonCollisions(BundleCapability singleton,
					Collection<BundleCapability> collisionCandidates) { }

			public void filterMatches(BundleRequirement requirement,
					Collection<BundleCapability> candidates) { }

			public void end() { }
			
		}
		public ResolverHook begin(Collection<BundleRevision> triggers) {
			if (triggers.containsAll(targetTriggers)) {
				// this is the triggers we are looking for
				return new DiagResolverHook();
			}
			// did not find the expected triggers do not participate
			// in the resolve process to gather the report
			return null;
		}
		ResolutionReport getReport() {
			return report;
		}
	}
 * </pre> 
 * @since 3.10
 */
public interface ResolutionReport {
	public interface Entry {
		enum Type {
			/**
			 * Indicates a resource failed to resolve because a resolver hook
			 * filtered it out. The structure of the data is <code>null</code>.
			 */
			// TODO This could possibly be improved by adding a reference to the
			// hook that filtered it out.
			FILTERED_BY_RESOLVER_HOOK,
			/**
			 * Indicates a resource failed to resolve because no capabilities
			 * matching one of the resource's requirements could not be found.
			 * The structure of the data is <code>Requirement</code>, which
			 * represents the missing requirement.
			 */
			MISSING_CAPABILITY,
			/**
			 * Indicates a resource failed to resolve because (1) it's a
			 * singleton, (2) there was at least one collision, and (3) it was
			 * not the selected singleton. The structure of the data is <code>
			 * Resource</code>, which represents the singleton with which the
			 * resource collided.
			 */
			// TODO Should the data present all of the colliding singletons
			// rather than multiple entries each containing a single collision?
			SINGLETON_SELECTION,
			/**
			 * Indicates a resource failed to resolve because one or more
			 * providers of capabilities matching the resource's requirements
			 * were not resolved. The structure of the data is <code>
			 * Map&lt;Requirement, Set&lt;Capability&gt;&gt;</code>.
			 */
			UNRESOLVED_PROVIDER
		}

		// TODO Can this make use of generics? Or should this be Map<String, Object>
		// and each enum would define the key constants?
		/**
		 * Returns the data associated with this resolution report entry.  The 
		 * structure of the data is determined by the <code>Type</code>
		 * of the entry and may by <code>null</code>.
		 * 
		 * @return the data associated with this resolution report entry.
		 * @see Type
		 */
		Object getData();

		/**
		 * Returns the type for this resolution report entry.
		 * @return the type for this resolution report entry.
		 */
		Type getType();
	}

	/**
	 * A resolution report listener gets called an the end of resolve process
	 * in order to receive a resolution report.  All {@link ResolverHook resolver hooks}
	 * that also implement the {@link Listener listener} interface will be called
	 * to receive the resolution report associated with the resolve process.
	 */
	public interface Listener {
		void handleResolutionReport(ResolutionReport report);
	}

	/**
	 * Returns all resolution report entries associated with this report.
	 * The key is the unresolved resource and the value is a list of 
	 * report entries that caused the associated resource  to not be able to resolve.
	 * @return all resolution report entries associated with this report.
	 */
	Map<Resource, List<Entry>> getEntries();
}
