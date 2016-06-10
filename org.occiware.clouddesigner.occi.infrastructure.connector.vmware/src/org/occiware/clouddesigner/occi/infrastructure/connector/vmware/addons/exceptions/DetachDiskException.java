package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions;

public class DetachDiskException extends Exception {

	public DetachDiskException() {
	}

	public DetachDiskException(String message) {
		super(message);
	}

	public DetachDiskException(Throwable cause) {
		super(cause);
	}

	public DetachDiskException(String message, Throwable cause) {
		super(message, cause);
	}

	public DetachDiskException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
