package br.ufal.cideei.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;

import org.eclipse.jface.text.source.AnnotationPainter.IDrawingStrategy;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.jface.text.source.AnnotationPainter.IDrawingStrategy;

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
		
		try{
			IWorkbenchPartSite site = getSite();
			IWorkbenchWindow window = site.getWorkbenchWindow();
			window.getSelectionService().addPostSelectionListener(new ChangeSelectionListener());
		}catch (Exception e){
			e.printStackTrace();
		}
		
		ProjectionViewer viewer = (ProjectionViewer) getSourceViewer();
		/*
		ProjectionSupport projectionSupport_ = new ProjectionSupport(viewer, getAnnotationAccess(), getSharedColors());
	    projectionSupport_.install();
	    projectionSupport_.setAnnotationPainterDrawingStrategy(new IDrawingStrategy(){
	    	public void draw(Annotation annotation, GC gc, StyledText textWidget, int offset, int length, Color color) {
	    		if (gc != null) {
	    			color = new Color(Display.getCurrent(), new RGB(255,0,0));
	    			Point left= textWidget.getLocationAtOffset(offset);
	    			//textWidget.setSelectionBackground(color);
	    			int x1= left.x;
	    			int verticalOffset = textWidget.getLineHeight()/2;
	    			gc.setForeground(color);
	    			gc.setBackground(color);
	    			gc.drawLine(x1, left.y + verticalOffset , textWidget.getBounds().width, left.y + verticalOffset);
	    			//gc.setClipping(x, y, width, height);
	    		} else {
	    			textWidget.redrawRange(offset, length, true);
	    		}			
	    	}
	    });
		*/
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
			annotation.markCollapsed();
			
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
