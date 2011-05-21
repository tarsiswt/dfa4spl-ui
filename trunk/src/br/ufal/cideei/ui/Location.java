package br.ufal.cideei.ui;

import org.eclipse.core.resources.IFile;

public class Location {
	
	private IFile file;
	private int lineNumber;
	private String configuration;
	private String feature;

	public String getConfiguration() {
		return configuration;
	}
	public void setConfiguration(String configuration) {
		this.configuration = configuration;
	}
	public String getFeature() {
		return feature;
	}
	public void setFeature(String feature) {
		this.feature = feature;
	}
	
	public IFile getFile() {
		return file;
	}
	public void setFile(IFile file) {
		this.file = file;
	}
	public int getLineNumber() {
		return lineNumber;
	}
	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}
	
	
	
}
