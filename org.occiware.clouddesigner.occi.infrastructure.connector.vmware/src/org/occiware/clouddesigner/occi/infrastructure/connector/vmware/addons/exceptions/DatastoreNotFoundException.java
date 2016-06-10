package org.occiware.clouddesigner.occi.infrastructure.connector.vmware.addons.exceptions;

public class DatastoreNotFoundException extends Exception {

	private static final long serialVersionUID = 7313975719905734910L;

	public DatastoreNotFoundException() {
		
	}

	public DatastoreNotFoundException(String message) {
		super(message);
		
	}

	public DatastoreNotFoundException(Throwable cause) {
		super(cause);
		
	}

	public DatastoreNotFoundException(String message, Throwable cause) {
		super(message, cause);
		
	}

	public DatastoreNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
		
	}

}
