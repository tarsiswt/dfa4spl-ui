package br.ufal.cideei.ui;

import org.eclipse.core.resources.IFile;

public class Location {
	
	private IFile file;
	private int lineNumber;
	
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
