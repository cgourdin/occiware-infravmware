/**
 * Copyright (c) 2016 Inria
 *  
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * - Christophe Gourdin <christophe.gourdin@inria.fr>
 *
 */
package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions;

public class AttachDiskException extends Exception {

	public AttachDiskException() {
	}

	public AttachDiskException(String message) {
		super(message);
	}

	public AttachDiskException(Throwable cause) {
		super(cause);
	}

	public AttachDiskException(String message, Throwable cause) {
		super(message, cause);
	}

	public AttachDiskException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
