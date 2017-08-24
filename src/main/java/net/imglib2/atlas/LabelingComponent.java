package net.imglib2.atlas;

import bdv.util.*;
import bdv.viewer.DisplayMode;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.atlas.actions.ToggleVisibility;
import net.imglib2.atlas.color.ColorMapProvider;
import net.imglib2.atlas.color.UpdateColormap;
import net.imglib2.atlas.control.brush.*;
import net.imglib2.atlas.labeling.Labeling;
import net.imglib2.atlas.labeling.LabelsLayer;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Intervals;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.Behaviours;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LabelingComponent {

	private BdvHandle bdvHandle;

	private InputTriggerConfig config = new InputTriggerConfig();

	private Actions actions = new Actions( config );

	private Behaviours behaviors = new Behaviours(config);

	private JPanel panel = new JPanel();

	private final JFrame dialogBoxOwner;

	private final List<AbstractNamedAction> actionsList = new ArrayList();

	private ColorMapProvider colorProvider;

	private Holder<Labeling> labels;

	public LabelingComponent(JFrame dialogBoxOwner) {
		this.dialogBoxOwner = dialogBoxOwner;
	}

	public JComponent getComponent() {
		return panel;
	}

	public List<AbstractNamedAction> getActions() {
		return actionsList;
	}

	@SuppressWarnings( { "rawtypes" } )
	public < R extends NumericType< R >>
	BdvHandle trainClassifier(
			final RandomAccessibleInterval<R> rawData,
			final List<String> labels,
			final boolean isTimeSeries)
	{
		final int nDim = rawData.numDimensions();

		initBdv(isTimeSeries || nDim != 3);

		initLabelsLayer(labels, rawData, isTimeSeries);

		addAction(new ToggleVisibility( "Toggle Classification", bdvHandle.getViewerPanel(), 1 ), "C");

		BdvFunctions.show(rawData, "original", BdvOptions.options().addTo( bdvHandle ));

		return bdvHandle;
	}

	private static int[] cellDimensions(CellGrid grid) {
		final int[] cellDimensions = new int[ grid.numDimensions() ];
		grid.cellDimensions( cellDimensions );
		return cellDimensions;
	}

	public void addAction(AbstractNamedAction action, String keyStroke) {
		JMenuItem item = new JMenuItem(action);
		actionsList.add(action);
		actions.namedAction(action, keyStroke);
		actions.install( bdvHandle.getKeybindings(), "classifier training" );
	}

	public void addBehaviour(Behaviour behaviour, String name, String defaultTriggers) {
		behaviors.behaviour(behaviour, name, defaultTriggers);
		behaviors.install( bdvHandle.getTriggerbindings(), "classifier training" );
	}

	public Labeling getLabeling() {
		return labels.get();
	}

	private void initBdv(boolean is2D) {
		final BdvOptions options = BdvOptions.options();
		if (is2D)
			options.is2D();
		bdvHandle = new BdvHandlePanel(dialogBoxOwner, options);
		panel.setLayout(new BorderLayout());
		panel.add(bdvHandle.getViewerPanel());
		bdvHandle.getViewerPanel().setDisplayMode( DisplayMode.FUSED );
	}

	private PaintPixelsGenerator<IntType, ? extends Iterator<IntType>> initPixelGenerator(boolean isTimeSeries, int numDimensions) {
		if ( isTimeSeries )
			return new NeighborhoodPixelsGeneratorForTimeSeries<>(numDimensions - 1, new NeighborhoodPixelsGenerator<IntType>(NeighborhoodFactories.hyperSphere(), 1.0));
		else
			return new NeighborhoodPixelsGenerator<>( NeighborhoodFactories.< IntType >hyperSphere(), 1.0 );
	}

	private void initLabelsLayer(List<String> labels, Interval interval, boolean isTimeSeries) {
		this.labels = new Holder<>(new Labeling(labels, interval));
		colorProvider = new ColorMapProvider(this.labels);

		BdvFunctions.show( new LabelsLayer(this.labels, colorProvider, this).view(), "labels", BdvOptions.options().addTo(bdvHandle) );
		final LabelBrushController brushController = new LabelBrushController(
				bdvHandle.getViewerPanel(),
				this.labels,
				initPixelGenerator(isTimeSeries, this.labels.get().numDimensions()),
				behaviors,
				colorProvider.colorMap() );
		behaviors.install( bdvHandle.getTriggerbindings(), "classifier training" );
		initColorMapUpdaterAction(labels, colorProvider);
		addAction(new ToggleVisibility( "Toggle Labels", bdvHandle.getViewerPanel(), 0 ), "L");
		bdvHandle.getViewerPanel().getDisplay().addOverlayRenderer( brushController.getBrushOverlay() );
	}

	private void initColorMapUpdaterAction(List<String> labels, ColorMapProvider colorProvider) {
		final UpdateColormap colormapUpdater = new UpdateColormap( colorProvider, labels, bdvHandle.getViewerPanel(), 1.0f );
		addAction(colormapUpdater, "ctrl shift C");
	}

	public ColorMapProvider colorProvider() {
		return colorProvider;
	}

	public void setLabeling(Labeling labeling) {
		if(! Intervals.equals(labels.get(), labeling))
			throw new IllegalArgumentException();
		labels.set(labeling);
	}

	public void requestRepaint() {
		bdvHandle.getViewerPanel().requestRepaint();
	}
}
