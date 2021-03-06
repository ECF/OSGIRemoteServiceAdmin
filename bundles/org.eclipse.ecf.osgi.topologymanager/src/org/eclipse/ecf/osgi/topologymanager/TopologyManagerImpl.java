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

import java.util.Map;

import org.eclipse.ecf.osgi.services.remoteserviceadmin.AbstractTopologyManager;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.EndpointDescription;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;

public class TopologyManagerImpl extends AbstractTopologyManager implements EndpointEventListener {

	public TopologyManagerImpl(BundleContext context) {
		super(context);
	}

	protected String getFrameworkUUID() {
		return super.getFrameworkUUID();
	}

	protected void handleEndpointAdded(org.osgi.service.remoteserviceadmin.EndpointDescription endpoint,
			String matchedFilter) {
		handleECFEndpointAdded((EndpointDescription) endpoint);
	}

	protected void handleEndpointRemoved(org.osgi.service.remoteserviceadmin.EndpointDescription endpoint,
			String matchedFilter) {
		handleECFEndpointRemoved((EndpointDescription) endpoint);
	}

	// EventListenerHook impl
	protected void handleEvent(ServiceEvent event, @SuppressWarnings("rawtypes") Map listeners) {
		super.handleEvent(event, listeners);
	}

	// RemoteServiceAdminListener impl
	protected void handleRemoteAdminEvent(RemoteServiceAdminEvent event) {
		if (!(event instanceof RemoteServiceAdmin.RemoteServiceAdminEvent))
			return;
		RemoteServiceAdmin.RemoteServiceAdminEvent rsaEvent = (RemoteServiceAdmin.RemoteServiceAdminEvent) event;

		int eventType = event.getType();
		EndpointDescription endpointDescription = rsaEvent.getEndpointDescription();

		switch (eventType) {
		case RemoteServiceAdminEvent.EXPORT_REGISTRATION:
			advertiseEndpointDescription(endpointDescription);
			break;
		case RemoteServiceAdminEvent.EXPORT_UNREGISTRATION:
			unadvertiseEndpointDescription(endpointDescription);
			break;
		case RemoteServiceAdminEvent.EXPORT_ERROR:
			logError("handleRemoteAdminEvent.EXPORT_ERROR", "Export error with event=" + rsaEvent); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case RemoteServiceAdminEvent.EXPORT_WARNING:
			logWarning("handleRemoteAdminEvent.EXPORT_WARNING", "Export warning with event=" + rsaEvent); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case RemoteServiceAdminEvent.EXPORT_UPDATE:
			advertiseModifyEndpointDescription(endpointDescription);
			break;
		case RemoteServiceAdminEvent.IMPORT_REGISTRATION:
			break;
		case RemoteServiceAdminEvent.IMPORT_UNREGISTRATION:
			break;
		case RemoteServiceAdminEvent.IMPORT_ERROR:
			logError("handleRemoteAdminEvent.IMPORT_ERROR", "Import error with event=" + rsaEvent); //$NON-NLS-1$//$NON-NLS-2$
			break;
		case RemoteServiceAdminEvent.IMPORT_WARNING:
			logWarning("handleRemoteAdminEvent.IMPORT_WARNING", "Import warning with event=" + rsaEvent); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		default:
			logWarning("handleRemoteAdminEvent", //$NON-NLS-1$
					"RemoteServiceAdminEvent=" + rsaEvent + " received with unrecognized type"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	public void endpointChanged(EndpointEvent event, String matchedFilter) {
		int eventType = event.getType();
		org.osgi.service.remoteserviceadmin.EndpointDescription ed = event.getEndpoint();
		switch (eventType) {
		case EndpointEvent.ADDED:
			handleEndpointAdded(ed, matchedFilter);
			break;
		case EndpointEvent.REMOVED:
			handleEndpointRemoved(ed, matchedFilter);
			break;
		case EndpointEvent.MODIFIED:
			handleEndpointModified(ed, matchedFilter);
			break;
		case EndpointEvent.MODIFIED_ENDMATCH:
			handleEndpointModifiedEndmatch(ed, matchedFilter);
			break;
		}
	}

	protected void handleEndpointModifiedEndmatch(org.osgi.service.remoteserviceadmin.EndpointDescription endpoint,
			String matchedFilter) {
		// By default do nothing for end match. subclasses may decide
		// to change this behavior
	}

	protected void handleEndpointModified(org.osgi.service.remoteserviceadmin.EndpointDescription endpoint,
			String matchedFilter) {
		handleECFEndpointModified((EndpointDescription) endpoint);
	}
}
