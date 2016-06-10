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
