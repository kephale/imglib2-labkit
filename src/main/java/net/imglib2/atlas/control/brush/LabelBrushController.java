package net.imglib2.atlas.control.brush;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import net.imglib2.*;
import net.imglib2.atlas.Holder;
import net.imglib2.atlas.labeling.Labeling;
import net.imglib2.roi.IterableRegion;
import net.imglib2.type.logic.BitType;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.ScrollBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.viewer.ViewerPanel;
import net.imglib2.atlas.BrushOverlay;
import net.imglib2.atlas.color.IntegerColorProvider;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;

/**
 * A {@link TransformEventHandler} that changes an {@link AffineTransform3D}
 * through a set of {@link Behaviour}s.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 * @author Philipp Hanslovsky
 */
public class LabelBrushController
{

	public static int BACKGROUND = -1;

	final protected ViewerPanel viewer;

	private List<IterableRegion<BitType>> labels;

	private final PaintPixelsGenerator< BitType, ? extends Iterator<BitType> > pixelsGenerator;

	final protected AffineTransform3D labelTransform = new AffineTransform3D();

	final protected RealPoint labelLocation;

	final protected BrushOverlay brushOverlay;

	final int brushNormalAxis;

	protected int brushRadius = 5;

	private int currentLabel = 0;

	public BrushOverlay getBrushOverlay()
	{
		return brushOverlay;
	}

	public int getCurrentLabel()
	{
		return currentLabel;
	}

	public void setCurrentLabel( final int label )
	{
		this.currentLabel = label;
	}

	/**
	 * Coordinates where mouse dragging started.
	 */
	private int oX, oY;

	public LabelBrushController(
			final ViewerPanel viewer,
			final Holder<Labeling> labels,
			final PaintPixelsGenerator pixelsGenerator,
			final Behaviours behaviors,
			final int brushNormalAxis,
			final IntegerColorProvider colorProvider)
	{
		this.viewer = viewer;
		this.pixelsGenerator = pixelsGenerator;
		this.brushNormalAxis = brushNormalAxis;
		updateLabeling(labels.get());
		labels.notifier().add(this::updateLabeling);
		brushOverlay = new BrushOverlay( viewer, currentLabel, colorProvider );

		labelLocation = new RealPoint( 3 );

		behaviors.behaviour( new Paint(), "paint", "SPACE button1" );
		behaviors.behaviour( new Erase(), "erase", "SPACE button2", "SPACE button3" );
		behaviors.behaviour( new ChangeBrushRadius(), "change brush radius", "SPACE scroll" );
		behaviors.behaviour( new ChangeLabel(), "change label", "SPACE shift scroll" );
		behaviors.behaviour( new MoveBrush(), "move brush", "SPACE" );
	}

	void updateLabeling(Labeling labeling) {
		this.labels = new ArrayList<>(labeling.regions().values());
		currentLabel = Math.min(currentLabel, labels.size());
	}

	public LabelBrushController(
			final ViewerPanel viewer,
			final Holder<Labeling> labels,
			final PaintPixelsGenerator pixelsGenerator,
			final Behaviours behaviors,
			final IntegerColorProvider colorProvider)
	{
		this( viewer, labels, pixelsGenerator, behaviors, 2, colorProvider );
	}

	private void setCoordinates( final int x, final int y )
	{
		labelLocation.setPosition( x, 0 );
		labelLocation.setPosition( y, 1 );
		labelLocation.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelLocation );

		labelTransform.applyInverse( labelLocation, labelLocation );
	}

	private abstract class AbstractPaintBehavior implements DragBehaviour
	{
		protected void paint( final RealLocalizable coords)
		{
			synchronized ( viewer )
			{
				final int v = getValue();
				IterableRegion<BitType> label = labels.get(v);
				final RandomAccessible<BitType> extended = Views.extendValue(label, new BitType(false));
				final Iterator< BitType > it = pixelsGenerator.getPaintPixels( extended, coords, viewer.getState().getCurrentTimepoint(), brushRadius );
				while ( it.hasNext() )
				{
					final BitType val = it.next();
					if ( Intervals.contains( label, ( Localizable ) it ) )
						val.set( doPaint() );
				}
			}

		}

		protected void paint( final int x, final int y )
		{
			setCoordinates( x, y );
			paint( labelLocation );
		}

		protected void paint( final int x1, final int y1, final int x2, final int y2 )
		{
			setCoordinates( x1, y1 );
			final double[] p1 = new double[ 3 ];
			final RealPoint rp1 = RealPoint.wrap( p1 );
			labelLocation.localize( p1 );

			setCoordinates( x2, y2 );
			final double[] d = new double[ 3 ];
			labelLocation.localize( d );

			LinAlgHelpers.subtract( d, p1, d );

			final double l = LinAlgHelpers.length( d );
			LinAlgHelpers.normalize( d );

			for ( int i = 1; i < l; ++i )
			{
				LinAlgHelpers.add( p1, d, p1 );
				paint( rp1 );
			}
			paint( labelLocation );
		}

		private int getValue()
		{
			return getCurrentLabel();
		}

		@Override
		public void init( final int x, final int y )
		{
			synchronized ( this )
			{
				oX = x;
				oY = y;
			}

			paint( x, y );

			viewer.requestRepaint();
		}

		@Override
		public void drag( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );

			paint( oX, oY, x, y );

			synchronized ( this )
			{
				oX = x;
				oY = y;
			}

			viewer.requestRepaint();
		}

		@Override
		public void end( final int x, final int y )
		{
		}

		protected abstract boolean doPaint();
	}

	private class Paint extends AbstractPaintBehavior
	{
		@Override
		protected boolean doPaint() {
			return true;
		}
	}

	private class Erase extends AbstractPaintBehavior
	{
		@Override
		protected boolean doPaint() {
			return false;
		}
	}

	private class ChangeBrushRadius implements ScrollBehaviour
	{
		@Override
		public void scroll( final double wheelRotation, final boolean isHorizontal, final int x, final int y )
		{
			if ( !isHorizontal )
			{
				if ( wheelRotation < 0 )
					brushRadius += 1;
				else if ( wheelRotation > 0 )
					brushRadius = Math.max( 0, brushRadius - 1 );

				brushOverlay.setRadius( brushRadius );
				// TODO request only overlays to repaint
				viewer.getDisplay().repaint();
			}
		}
	}

	private class ChangeLabel implements ScrollBehaviour
	{

		@Override
		public void scroll( final double wheelRotation, final boolean isHorizontal, final int x, final int y )
		{
			if ( !isHorizontal )
			{
				if ( wheelRotation < 0 )
					currentLabel = Math.min( currentLabel + 1, labels.size() - 1 );
				else if ( wheelRotation > 0 )
					currentLabel = Math.max( currentLabel - 1, 0 );

				brushOverlay.setLabel( currentLabel );
				// TODO request only overlays to repaint
				viewer.getDisplay().repaint();
			}
		}
	}

	private class MoveBrush implements DragBehaviour
	{

		@Override
		public void init( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );
			brushOverlay.setVisible( true );
			// TODO request only overlays to repaint
			viewer.setCursor( Cursor.getPredefinedCursor( Cursor.CROSSHAIR_CURSOR ) );
			viewer.getDisplay().repaint();
		}

		@Override
		public void drag( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );
		}

		@Override
		public void end( final int x, final int y )
		{
			brushOverlay.setVisible( false );
			// TODO request only overlays to repaint
			viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			viewer.getDisplay().repaint();

		}
	}
}
