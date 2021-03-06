/*******************************************************************************
 * Copyright (c) 2017 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.osgi.topologymanager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyManager
		implements EventListenerHook, RemoteServiceAdminListener, ITopologyManager {

	protected static final Logger logger = LoggerFactory.getLogger(TopologyManager.class);

	class EndpointEventHolder {
		private final EndpointDescription endpointDescription;
		private final String filter;

		public EndpointEventHolder(EndpointDescription d, String f) {
			this.endpointDescription = d;
			this.filter = f;
		}

		public EndpointDescription getEndpoint() {
			return this.endpointDescription;
		}

		public String getFilter() {
			return this.filter;
		}
	}

	class ProxyEndpointEventListener implements EndpointEventListener {

		private final Bundle bundle;

		public ProxyEndpointEventListener(Bundle b) {
			this.bundle = b;
		}

		public void endpointChanged(EndpointEvent event, String filter) {
			int type = event.getType();
			if (type == EndpointEvent.ADDED) {
				synchronized (bundleEndpointEventListenerMap) {
					List<EndpointEventHolder> endpointEventHolders = bundleEndpointEventListenerMap.get(this.bundle);
					if (endpointEventHolders == null)
						endpointEventHolders = new ArrayList<EndpointEventHolder>();
					endpointEventHolders.add(new EndpointEventHolder(event.getEndpoint(), filter));
					bundleEndpointEventListenerMap.put(this.bundle, endpointEventHolders);
				}
			} else if (type == EndpointEvent.REMOVED) {
				synchronized (bundleEndpointEventListenerMap) {
					List<EndpointEventHolder> endpointEventHolders = bundleEndpointEventListenerMap.get(this.bundle);
					if (endpointEventHolders != null) {
						for (Iterator<EndpointEventHolder> i = endpointEventHolders.iterator(); i.hasNext();) {
							EndpointEventHolder eh = i.next();
							EndpointDescription oldEd = eh.getEndpoint();
							EndpointDescription newEd = event.getEndpoint();
							if (oldEd.equals(newEd))
								i.remove();
						}
						if (endpointEventHolders.size() == 0)
							bundleEndpointEventListenerMap.remove(this.bundle);
					}

				}
			}
			deliverSafe(event, filter);
		}

		private void logError(String methodName, String message, Throwable e) {
			logger.error(((methodName == null) ? "<unknown>" //$NON-NLS-1$
					: methodName) + ":" //$NON-NLS-1$
					+ ((message == null) ? "<empty>" //$NON-NLS-1$
							: message),
					e);
		}

		private void deliverSafe(EndpointEvent endpointEvent, String matchingFilter) {
			EndpointEventListener listener = topologyManagerImpl;
			SafeRunner.run(new ISafeRunnable() {
				@Override
				public void run() throws Exception {
					if (listener != null)
						listener.endpointChanged(endpointEvent, matchingFilter);
				}

				public void handleException(Throwable exception) {
					String message = "Exception in EndpointEventListener listener=" //$NON-NLS-1$
							+ listener + " event=" //$NON-NLS-1$
							+ endpointEvent + " matchingFilter=" //$NON-NLS-1$
							+ matchingFilter;
					logError("deliverSafe", message, exception); //$NON-NLS-1$
				};
			});
		}

		public void deliverRemoveEventForBundle(EndpointEventHolder eventHolder) {
			deliverSafe(new EndpointEvent(EndpointEvent.REMOVED, eventHolder.getEndpoint()), eventHolder.getFilter());
		}
	}

	private Map<Bundle, List<EndpointEventHolder>> bundleEndpointEventListenerMap = new HashMap<Bundle, List<EndpointEventHolder>>();

	protected TopologyManagerImpl topologyManagerImpl;
	protected ServiceRegistration<?> endpointListenerRegistration;
	protected List<String> matchingFilters;

	protected void activate(BundleContext context, Map<String, ?> properties) throws Exception {
		String endpointConditionalOp = (String) properties.get(ENDPOINT_CONDITIONAL_OP_PROP);
		if (endpointConditionalOp == null)
			endpointConditionalOp = ENDPOINT_CONDITIONAL_OP;

		Boolean endpointAllowLocalhost = (Boolean) properties.get(ENDPOINT_ALLOWLOCALHOST_PROP);
		if (endpointAllowLocalhost == null)
			endpointAllowLocalhost = ENDPOINT_ALLOWLOCALHOST;

		String extraFilters = (String) properties.get(ENDPOINT_EXTRA_FILTERS_PROP);
		if (extraFilters == null)
			extraFilters = ENDPOINT_EXTRA_FILTERS;

		String[] extraFiltersArr = null;
		if (extraFilters != null)
			extraFiltersArr = extraFilters.split(",");

		String extraConditional = (String) properties.get(ENDPOINT_EXTRA_CONDITIONAL_PROP);
		if (extraConditional == null)
			extraConditional = ENDPOINT_EXTRA_CONDITIONAL;
		this.matchingFilters = Collections.synchronizedList(new ArrayList<String>());
		this.topologyManagerImpl = new TopologyManagerImpl(context);
		StringBuffer elScope = new StringBuffer(""); //$NON-NLS-1$
		if (endpointConditionalOp != null && !"".equals(endpointConditionalOp)) {
			elScope.append("(").append(endpointConditionalOp).append("(");
			if (!ENDPOINT_ALLOWLOCALHOST)
				elScope.append("!(").append(org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_FRAMEWORK_UUID)
						.append("=").append(topologyManagerImpl.getFrameworkUUID()).append(")");
			elScope.append(")");
			elScope.append(ONLY_ECF_SCOPE);
			if (extraConditional != null && !"".equals(extraConditional))
				elScope.append(extraConditional);
			elScope.append(")");
		}
		String elString = elScope.toString();
		if (!"".equals(elString))
			matchingFilters.add(elString);

		if (extraFiltersArr != null)
			for (String filter : extraFiltersArr)
				if (filter != null && !"".equals(filter))
					matchingFilters.add(filter);

		Dictionary<String, Object> props = createEndpointListenerProps(matchingFilters);

		endpointListenerRegistration = context.registerService(EndpointEventListener.class,
				new ServiceFactory<EndpointEventListener>() {
					public EndpointEventListener getService(Bundle bundle,
							ServiceRegistration<EndpointEventListener> registration) {
						return new ProxyEndpointEventListener(bundle);
					}

					public void ungetService(Bundle bundle, ServiceRegistration<EndpointEventListener> registration,
							EndpointEventListener service) {
						ProxyEndpointEventListener peel = (service instanceof ProxyEndpointEventListener)
								? (ProxyEndpointEventListener) service
								: null;
						if (peel == null)
							return;
						synchronized (bundleEndpointEventListenerMap) {
							List<EndpointEventHolder> endpointEventHolders = bundleEndpointEventListenerMap.get(bundle);
							if (endpointEventHolders != null)
								for (EndpointEventHolder eh : endpointEventHolders)
									peel.deliverRemoveEventForBundle(eh);
						}
					}
				}, (Dictionary<String, Object>) props);
	}

	protected Dictionary<String, Object> createEndpointListenerProps(List<String> filters) {
		Hashtable<String, Object> props = new Hashtable<String, Object>();
		props.put(org.osgi.service.remoteserviceadmin.EndpointEventListener.ENDPOINT_LISTENER_SCOPE,
				matchingFilters.toArray(new String[filters.size()]));
		return props;
	}

	protected void deactivate() {
		if (endpointListenerRegistration != null) {
			endpointListenerRegistration.unregister();
			endpointListenerRegistration = null;
		}
		if (this.topologyManagerImpl != null) {
			this.topologyManagerImpl.close();
			this.topologyManagerImpl = null;
		}
		if (this.matchingFilters != null) {
			this.matchingFilters.clear();
			this.matchingFilters = null;
		}
	}

	// RemoteServiceAdminListener impl
	public void remoteAdminEvent(RemoteServiceAdminEvent event) {
		if (topologyManagerImpl == null)
			return;
		topologyManagerImpl.handleRemoteAdminEvent(event);
	}

	// EventListenerHook impl
	public void event(ServiceEvent event, @SuppressWarnings("rawtypes") Map listeners) {
		if (topologyManagerImpl == null)
			return;
		topologyManagerImpl.handleEvent(event, listeners);
	}

	@Override
	public String[] getEndpointFilters() {
		return this.matchingFilters.toArray(new String[this.matchingFilters.size()]);
	}

	@Override
	public String[] setEndpointFilters(String[] newFilters) {
		List<String> f = this.matchingFilters;
		if (f == null || newFilters == null)
			return null;
		List<String> result = new ArrayList<String>(f);
		synchronized (f) {
			f.clear();
			for (int i = 0; i < newFilters.length; i++)
				f.add(newFilters[i]);
		}
		if (endpointListenerRegistration != null)
			endpointListenerRegistration.setProperties(createEndpointListenerProps(Arrays.asList(newFilters)));
		return result.toArray(new String[result.size()]);
	}

}
