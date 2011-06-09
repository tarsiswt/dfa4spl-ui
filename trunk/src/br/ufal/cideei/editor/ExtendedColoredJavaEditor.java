package br.ufal.cideei.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.widgets.Composite;

import de.ovgu.cide.language.jdt.editor.ColoredCompilationUnitEditor;

public class ExtendedColoredJavaEditor extends ColoredCompilationUnitEditor{
	
	public static final String EDITOR_CIDEEI_ID = "br.ufal.cideei.editor.ColoredFoldingCompilationUnitEditor";
	
	private HashSet<ArrayList<Position>> positionsCIDEEI;
	private Annotation[] oldAnnotationsCIDEEI;
	private ProjectionAnnotationModel annotationModelCIDEEI;
	
	public ExtendedColoredJavaEditor(){
		this.annotationModelCIDEEI = new ProjectionAnnotationModel();
	}
	
	public void instantiatePositions(){
		this.positionsCIDEEI = new HashSet<ArrayList<Position>>();
	}
	
	public HashSet<ArrayList<Position>> getPositions(){
		return this.positionsCIDEEI;
	}
	
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		
		ProjectionViewer viewer = (ProjectionViewer) getSourceViewer();

		viewer.disableProjection();
		viewer.enableProjection();
		
		this.annotationModelCIDEEI = viewer.getProjectionAnnotationModel();
		
	}
	
	public void updateFoldingStructure(ArrayList<Position> positions)
	{
		Annotation[] annotations = new Annotation[positions.size()];

		HashMap<ProjectionAnnotation,Position> newAnnotations = new HashMap<ProjectionAnnotation,Position>();
		
		for(int i =0;i<positions.size();i++)
		{
			ProjectionAnnotation annotation = new ProjectionAnnotation();
			
			newAnnotations.put(annotation,positions.get(i));
			annotationModelCIDEEI.addAnnotation(annotation, (Position) positions.get(i));
			
			annotations[i]=annotation;
		}
		
		oldAnnotationsCIDEEI=annotations;

	}
	
	public ProjectionAnnotationModel getProjectionAnnotationModel(){
		return this.annotationModelCIDEEI;
	}
	
}
