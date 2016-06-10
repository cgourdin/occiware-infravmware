package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions;

public class DiskNotFoundException extends Exception {

	private static final long serialVersionUID = 2043013836881626471L;

	public DiskNotFoundException() {
	}

	public DiskNotFoundException(String message) {
		super(message);
	}

	public DiskNotFoundException(Throwable cause) {
		super(cause);
	}

	public DiskNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public DiskNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
