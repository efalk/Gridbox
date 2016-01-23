/**
 * $Id: Gridbox.java,v 1.11 2012-05-05 06:01:38 falk Exp $
 *
 * Gridbox.java - Gridbox composite widget
 *
 * Author: Edward A. Falk
 *         efalk@users.sourceforge.net
 *
 * Date: May 2009
 *
 *
 */

package org.efalk.gridbox;

import java.util.Arrays;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/**
 * The Gridbox widget aligns its children in a rectangular array of cells.
 * Child widgets occupy a rectangular region of cells (default
 * size is 1x1).  A typical layout might look like this:
 *
 *      +-------+-------+---+--------------------------+
 *      | cell  | cell  |   |                          |
 *      |-------+-------| c |                          |
 *      | cell  | cell  | e |                          |
 *      |-------+-------| l |          cell            |
 *      | cell  | cell  | l |                          |
 *      |-------+-------|   |                          |
 *      | cell  | cell  |   |                          |
 *      |-------+-------+---+--------------------------|
 *      |         cell      |           cell           |
 *      +-------------------+--------------------------+
 *
 * In addition, child widgets may specify a margin, and weights which
 * determine how they are resized if the parent widget is resized.
 *
 * Gridbox attributes:
 *
 *      gridbox:gravity                 Default child gravity
 *      gridbox:inner_margin            Margin between children
 *      gridbox:force_uniform_width     boolean; all columns same width
 *      gridbox:force_uniform_height    boolean; all rows same height
 *
 *		Note: the force_uniform_* attributes work by assigning
 *		excess space to columns/rows in order to achieve a
 *		uniform size. If there is not enough excess space to
 *		accomplish this, Gridbox does the best it can.  Otherwise,
 *		all cells are made the same size, and then any remaining
 *		excess is distributed by weight.  If you really want a
 *		uniform size, then the weights should all be the same.
 *
 * Gridbox child layout attributes:
 *
 *      gridbox:layout_gridx            X position in the grid
 *      gridbox:layout_gridy            Y position in the grid
 *      gridbox:layout_colSpan          # columns it occupies
 *      gridbox:layout_rowSpan          # rows it occupies
 *      android:layout_width            basic width of the view
 *      android:layout_height           basic height of the view
 *      android:layout_margin
 *      android:layout_marginBottom
 *      android:layout_marginTop
 *      android:layout_marginLeft
 *      android:layout_marginRight
 *      gridbox:layout_gravity		How child is placed in cell
 *
 *      Gravity can be a combination of:
 *        left, right, center_horizontal, fill_horizontal
 *        top, bottom, center_vertical, fill_vertical
 *        center, fill
 *
 *   If there is extra space to allocate, the following attributes become
 *   meaningful:
 *
 *      gridbox:layout_weightx          How much of the excess horizontal/
 *      gridbox:layout_weighty            vertical space the cell should get
 *
 * Child grid positions do not need to be specified in any particular order;
 * it's perfectly acceptable to lay out by rows, by columns, or in any
 * other order you choose.  Placing two child widgets in the same cell is
 * undefined behavior.
 *
 * If not specified, the X and Y position of a cell in the grid default
 * to one cell to the right of the previous cell.
 * Column and row spans default to 1.
 *
 * The widget sizes should never be specified as fill_parent -- use
 * wrap_content or an explicit value instead.  If you want a child widget
 * to grow to fill its cell in the table, set its layout_gravity parameter
 * to FILL_HORIZONTAL and/or FILL_VERTICAL.
 *
 * The weight values determine how much of the extra space is assigned to
 * the row or column.  The child widget won't actually occupy the entire
 * cell unless you set gravity to FILL_HORIZONTAL and/or FILL_VERTICAL.
 *
 * If all weights are zero, excess space is distributed evenly, as if all
 * weights were one.
 *
 * Note:  Each column or row is assigned the maximum weightx/weighty value
 * of all of its cells.  This means that you can often assign a weight to
 * just one item to get the desired effect.
 */
public class Gridbox extends ViewGroup {
  /*
   * Based on Gridbox.c, written in 1998 for the X intrinsics library
   *
   * General theory of operation:
   *
   * Each child widget has its own "preferred" size, which is queried during
   * the geometry management process.
   *
   * Gridbox maintains arrays of preferred column widths and row heights
   * based on the maximum values of the child widgets in those rows and
   * columns.  Gridbox computes its own preferred size from this information.
   *
   * Gridbox always returns its own preferred size in response to
   * onMeasure() requests.
   *
   * When a child widget asks to be resized, Gridbox updates the
   * cached preferred size for the child, recomputes its own preferred
   * size accordingly, and asks its parent to be resized.  Once
   * negotiations with the parent are complete, Gridbox then computes
   * the new size of the child and responds to the child's request.
   *
   * Whenever the Gridbox is resized, it determines how much extra space there
   * is (if any), and distributes it among the rows and columns based on the
   * weights of those rows & columns.
   *
   *
   * Internal methods related to geometry management:
   *
   * GridboxResize()      given Gridbox size, lay out the child widgets.
   * layout()             given size, assign sizes of rows & columns
   * layoutChild()        assign size of one child widget
   * changeGeometry()     attempt to change size, negotiate with parent
   *
   */

  private static final String TAG = "Gridbox";

  private int gravity = Gravity.CENTER;
  private int innerMargin;              // Default distance between children
  private boolean force_uniform_width;
  private boolean force_uniform_height;

  private int ncol = 0, nrow = 0;       // Size of grid
  private int[] max_wids = null;        // Maximum widths requested
  private int[] max_hgts = null;        // Maximum heights requested
  private int[] wids = null;            // Assigned widths
  private int[] hgts = null;            // Assigned heights
  private float[] weightx = null;	// Column weights
  private float[] weighty = null;	// Row weights
  private int max_col_span, max_row_span;
  private int total_wid = 0, total_hgt = 0;
  private float total_weightx = 0, total_weighty = 0;
  private boolean need_count = true;
  // needs_layout = true;

/*
private int[] old_wids = null;
private int[] new_wids = null;
*/

  // private boolean mBaselineAligned = true;

  public Gridbox(Context ctx) {
    super(ctx);
    GridboxInit(ctx, null);
  }

  public Gridbox(Context ctx, AttributeSet attrs) {
    super(ctx, attrs);
    GridboxInit(ctx, attrs);
  }

  public Gridbox(Context ctx, AttributeSet attrs, int defStyle) {
    super(ctx, attrs, defStyle);
    GridboxInit(ctx, attrs);
  }

  private void GridboxInit(Context ctx, AttributeSet attrs)
  {
    TypedArray a = ctx.obtainStyledAttributes(attrs, R.styleable.Gridbox);
    innerMargin =
      a.getDimensionPixelSize(R.styleable.Gridbox_inner_margin, innerMargin);
    gravity = a.getInt(R.styleable.Gridbox_gravity, gravity);
    force_uniform_width =
      a.getBoolean(R.styleable.Gridbox_force_uniform_width, false);
    force_uniform_height =
      a.getBoolean(R.styleable.Gridbox_force_uniform_height, false);
    a.recycle();

    // TODO: resize now, or wait until later?
  }


  // Overrides

  /**
   * Add a child, specifying location within the grid.
   */
  @Override
  public void addView(View child, int col, int row) {
    super.addView(child);
    need_count = true;
  }

  /**
   * Add a child, specifying size and location within the grid.
   */
  public void addView(View child, int width, int height, int col, int row) {
    super.addView(child, width, height);
    need_count = true;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int wid = MeasureSpec.getSize(widthMeasureSpec);
    int hgt = MeasureSpec.getSize(heightMeasureSpec);
    final int num_children = getChildCount();
    final int hpad = getPaddingLeft() + getPaddingRight();
    final int vpad = getPaddingTop() + getPaddingBottom();
//Log.d(TAG, "onMeasure");

    // Overview:  Query all children, find out how much space they
    // need.  Each child has margins; add them in.  Once all the
    // children have been queried, we can determine the necessary
    // widths of the rows and columns.  Add them together, plus
    // padding, and that gives our own size.
    //
    // If any of the children have weights, they get applied to the
    // rows and columns as appropriate.  Each row or column has the
    // maximum weight of its children.  This will determine how the excess
    // space, if any, is distributed.
    //
    // Fill_parent is not a meaningful size parameter for a child of a
    // grid layout, so we treat it as wrap_content, but then give that
    // child a weight of 1 and gravity FILL so it can expand to fill space.
    //
    // This will almost certainly turn into a two-pass layout process.

    if( num_children <= 0 ) {
      // Degenerate case, just ask for our padding.
      wid = getSize(hpad, 0, widthMeasureSpec);
      hgt = getSize(vpad, 0, heightMeasureSpec);
      setMeasuredDimension(wid, hgt);
      return;
    }

    countCells();	// Find out the grid dimensions

    // TODO: when should we free these up?
    if( max_wids == null || max_wids.length < ncol ) max_wids = new int[ncol];
    if( max_hgts == null || max_hgts.length < nrow ) max_hgts = new int[nrow];
    if( wids == null || wids.length < ncol ) wids = new int[ncol];
    if( hgts == null || hgts.length < nrow ) hgts = new int[nrow];
    if( weightx == null || weightx.length < ncol ) weightx = new float[ncol];
    if( weighty == null || weighty.length < ncol ) weighty = new float[nrow];


    // Examine children for the size they need,
    // compute row & column sizes accordingly.
    //
    // There might be a more optimal way to do this to take advantage
    // of previous layout passes.  This is worth rethinking
    // if the Gridbox widget is to be used with large grids.
    //
    // This may also generate a non-optimum answer if large cells
    // partially overlap.

    Arrays.fill(max_wids, 0);
    Arrays.fill(max_hgts, 0);
    Arrays.fill(weightx, 0);
    Arrays.fill(weighty, 0);

//if (old_wids == null || old_wids.length != num_children) {
//  Log.d(TAG, "-- Number of children has changed");
//  old_wids = null;
//}

//new_wids = new int[num_children];

    // Find out how large children would like to be
    // TODO: we can skip this step if there haven't been any changes in
    // the children, their layout parameters, or their sizes.  But how to
    // determine that?
    total_wid = 0;
    total_hgt = 0;
    int i,j;
    for( i = 0; i < num_children; ++i )
    {
      final View child = getChildAt(i);
      if( child != null && child.getVisibility() != View.GONE )
      {
	final LayoutParams lp = (LayoutParams) child.getLayoutParams();
	if( lp.gravity == Gravity.NO_GRAVITY ) lp.gravity = gravity;

	// Ask child how much space it wants.  We'll be correcting later.
	measureChild(child, widthMeasureSpec, heightMeasureSpec);
      }
    }

/*
for( i = 0; i < num_children; ++i ) {
  final View child = getChildAt(i);
  new_wids[i] = child.getMeasuredWidth();
  if( old_wids != null && old_wids[i] != new_wids[i] ) {
    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
    Log.d(TAG,
      String.format("-- Child %d (%s: %d,%d) has changed requested width: %d => %d",
      	i, child.getClass().getName(), lp.gridx, lp.gridy,
	old_wids[i], new_wids[i]));
  }
}
*/

    // Find maximum column and row sizes.
    final int npass = Math.max(max_col_span, max_row_span);
    for( j = 1; j <= npass; ++j )
    {
      for( i = 0; i < num_children; ++i )
      {
	final View child = getChildAt(i);
	if( child != null && child.getVisibility() != View.GONE )
	{
	  final LayoutParams lp = (LayoutParams) child.getLayoutParams();
	  final int col = lp.gridx;
	  final int row = lp.gridy;
	  if( lp.colSpan == j ) {
	    final int w = child.getMeasuredWidth() +
			    lp.leftMargin + lp.rightMargin;
	    computeWidHgtUtil(col, j, w, lp.weightx, max_wids, weightx);
	  }
	  if( lp.rowSpan == j ) {
	    final int h = child.getMeasuredHeight() +
			    lp.topMargin + lp.bottomMargin;
	    computeWidHgtUtil(row, j, h, lp.weighty, max_hgts, weighty);
	  }
	}
      }
    }

    /*
    if( force_uniform_width )
      force_uniform(max_wids);

    if( force_uniform_height )
      force_uniform(max_hgts);
    */

    // Step 4: compute sums

    total_wid = 0;
    total_weightx = 0;
    for(i=0; i < ncol; ++i) {
      total_wid += max_wids[i];
      total_weightx += weightx[i];
    }

    total_hgt = 0;
    total_weighty = 0;
    for(i=0; i < nrow; ++i) {
      total_hgt += max_hgts[i];
      total_weighty += weighty[i];
    }

    wid = getSize(total_wid + hpad, total_weightx, widthMeasureSpec);
    hgt = getSize(total_hgt + vpad, total_weighty, heightMeasureSpec);
    setMeasuredDimension(wid, hgt);


    // We're done measuring ourself, but let's take a moment and inform
    // the children of the sizes they got.

    // Step 5: Compute the sizes
    System.arraycopy(max_wids, 0, wids, 0, wids.length);
    distributeExcess(ncol, wid - hpad, wids, total_wid,
			weightx, total_weightx, force_uniform_width);

    // Same again, for heights
    System.arraycopy(max_hgts, 0, hgts, 0, hgts.length);
    distributeExcess(nrow, hgt - vpad, hgts, total_hgt,
			weighty, total_weighty, force_uniform_height);


    // Step 6: Make a second pass, tell children the actual size they got
    // Remember that the sizes in the the row and column arrays include
    // margins, which we need to remove before informing the children.
    for( i = 0; i < num_children; ++i ) {
      final View child = getChildAt(i);
      if( child != null && child.getVisibility() != View.GONE )
      {
	final LayoutParams lp = (LayoutParams) child.getLayoutParams();
	// Only needs to be done if gravity is fill
	final int hgravity = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
	final int vgravity = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
	if( (hgravity == Gravity.FILL_HORIZONTAL) ||
	    (vgravity == Gravity.FILL_VERTICAL) )
	{
	  int width = hgravity == Gravity.FILL_HORIZONTAL ?
	      computeCellWid(lp) - lp.leftMargin - lp.rightMargin :
	      child.getMeasuredWidth();
	  int height = vgravity == Gravity.FILL_VERTICAL ?
	      computeCellHgt(lp) - lp.topMargin - lp.bottomMargin :
	      child.getMeasuredHeight();
/*
if( width != new_wids[i] )
  Log.d(TAG,
    String.format("-- Child %d (%d,%d) assigned a new width: %d => %d",
      i, lp.gridx, lp.gridy, new_wids[i], width));
*/
	  child.measure(
	    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
	    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
	}
      }
    }
    //old_wids = new_wids;
    //new_wids = null;
  }

  /*
   * Given the minimum required size for this gridlayout in 'size',
   * the total weight, and the measurespec passed down from the parent,
   * return the size we should set ourselves to.
   */
  static private final int
  getSize(int size, float weight, int spec) {
    int mode = MeasureSpec.getMode(spec);
    int wid = MeasureSpec.getSize(spec);
    switch( mode ) {
      default:
      case MeasureSpec.EXACTLY: return wid;
      case MeasureSpec.UNSPECIFIED: return size;
      case MeasureSpec.AT_MOST: return wid > size ? size : wid;
/*
	if( weight > 0 || size > wid) return wid;
	return size;
*/
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b)
  {
      final int count = getChildCount();
      int i;
      int x,y;

      // TODO: should we do another measure pass just in case the
      // values are different from the onMeasure() pass?  Nobody else does.

      if( count <= 0 || ncol <= 0 || nrow <= 0 )
        return;

      // assign positions
      int[] xs = new int[ncol];
      for(x=getPaddingLeft(), i=0; i < ncol; ++i)
      {
        xs[i] = x;
        x += wids[i];
      }

      int[] ys = new int[nrow];
      for(y=getPaddingTop(), i=0; i < nrow; ++i)
      {
        ys[i] = y;
        y += hgts[i];
      }

      // Finally, loop through children, assign positions and sizes
      // Each child is assigned a size which is a function of its position
      // and size in cells.  The child's margin is subtracted from all sides.

      //ncol = nrow = 0;
      //max_col_span = max_row_span = 0;
      for( i = 0; i < count; ++i ) {
        final View child = getChildAt(i);
        if( child != null && child.getVisibility() != View.GONE ) {
          layoutChild(child, xs, ys);
	}
      }
  }


  @Override
  public LayoutParams generateLayoutParams(AttributeSet attrs)
  {
    return new Gridbox.LayoutParams(getContext(), attrs);
  }

  @Override
  protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p)
  {
    return new Gridbox.LayoutParams(p);
  }

  @Override
  protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
    return p instanceof Gridbox.LayoutParams;
  }


  // Other public methods

  /**
   * Set the default child gravity.  All child cells which do not specify a
   * layout_gravity value will receive this value.
   */
  public Gridbox setGravity(int g) {
    if( gravity != g ) {
      gravity = g;
      requestLayout();
    }
    return this;
  }

  /**
   * Set inner margin.  This is the spacing that will be used between all
   * child cells which do not specify a margin.
   */
  public Gridbox setInnerMargin(int m) {
    if( innerMargin != m ) {
      innerMargin = m;
      requestLayout();
    }
    return this;
  }


  // Utilities

  /**
   * Find out how many cells wide and high this grid will be.
   * Assign grid locations where needed.
   */
  private void countCells() {
    if( !need_count ) {
      return;
    }
    final int count = getChildCount();
    ncol = nrow = 0;
    max_col_span = max_row_span = 0;
    int x = 0, y = 0;
    for( int i = 0; i < count; ++i ) {
      final View child = getChildAt(i);
      if( child != null && child.getVisibility() != View.GONE ) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if( lp.colSpan <= 0 ) lp.colSpan = 1;
        if( lp.rowSpan <= 0 ) lp.rowSpan = 1;
        if( lp.gridx < 0 ) lp.gridx = x;
        if( lp.gridy < 0 ) lp.gridy = y;
        x = lp.gridx;
        y = lp.gridy;
        if( x + lp.colSpan > ncol ) ncol = x + lp.colSpan;
        if( y + lp.rowSpan > nrow ) nrow = y + lp.rowSpan;
        x += lp.colSpan;
        if( lp.colSpan > max_col_span ) max_col_span = lp.colSpan;
        if( lp.rowSpan > max_row_span ) max_row_span = lp.rowSpan;
      }
    }
    need_count = false;
  }



  /*
  // Utility:  Force all items in sizes[] to be the same
  static private void force_uniform(int[] sizes) {
    int max_size = 0;
    int i, n = sizes.length;
    for( i = 0; i < n; ++i )
      if( sizes[i] > max_size ) max_size = sizes[i];
    for( i = 0; i < n; ++i )
      sizes[i] = max_size;
  }
  */



        // PRIVATE ROUTINES


  // Given a gridbox & child, compute the current size of
  // the cell(s) occupied by the child.

  private final int computeCellWid(final LayoutParams lp)
  {
    if( lp.colSpan == 1 ) return wids[lp.gridx];
    return computeCellSize(lp.gridx, lp.colSpan, wids);
  }

  private final int computeCellHgt(final LayoutParams lp)
  {
    if( lp.rowSpan == 1 ) return hgts[lp.gridy];
    return computeCellSize(lp.gridy, lp.rowSpan, hgts);
  }

  private final int computeCellSize(int idx, int n, int[] sizes)
  {
    int wid = 0;
    for(int i=0; i<n; ++i) wid += sizes[idx++];
    return wid;
  }


  // Utility: adjust size and weight arrays based on the location, size and
  // weight of the specified child.
  private static void
  computeWidHgtUtil(int idx, int ncell, int wid, float weight,
    int[] wids, float[] weights)
  {
    // 1 set the specified column weight(s) to the max of their current
    //   value and the weight of this widget.
    //
    // 2 find out if the available space in the indicated column(s)
    //   is enough to satisfy this widget.  If not, distribute the
    //   excess size by column weights.
    //
    //   The excess may not divide evenly into the number of cells.
    //   The remainder will also be distributed evenly to some of the
    //   cells.  Make a Bresenham walk to do this.

    if( idx < 0 || idx + ncell > wids.length ) {
      Log.e(TAG, "computeWidHgtUtil: idx="+idx + ", ncell="+ncell +
        ", length="+wids.length);
      return;
    }

    if( ncell == 1 )            // simple case
    {
      if( weights[idx] < weight ) weights[idx] = weight;
      if( wids[idx] < wid ) wids[idx] = wid;
      return;
    }

/*
    Puzzle:  imagine all single-cells are 4 pixels wide.  Imagine that
    there are some double-cells which are 16 pixels wide.  Arranged like
    this:

    naive (allocate excess size of double-wides evenly to columns):
          ....  ####    ....
          ####  ................
        ................  ####

    ideal:
        ....    ####    ....
        ####................
        ................####

    How can we achive the ideal layout?
*/

    // Conditionally assign weight to columns.
    for( int i=0; i < ncell; ++i)
      if( weight > weights[idx+i] ) weights[idx+i] = weight;
  }


  /**
   * Utility: distribute excess space across a number of cells.
   * weight of the specified child.
   * @param ncell    number of columsn/rows in region
   * @param size     total available size
   * @param sizes    array of widths/heights
   * @param stot     total size
   * @param weights  weights of the columns/rows
   * @param wtot     total weight
   * @param uniform  true if this function should try to make all sizes the same
   */
  private static void
  distributeExcess(int ncell, int size, int[] sizes, int stot,
	float[] weights, float wtot, boolean uniform)
  {
    if( ncell > sizes.length ) {
      Log.e(TAG, "distributeExcess: ncell="+ncell + ", length="+sizes.length);
      return;
    }

    // First, is there excess size to distribute?
    if (size <= stot)
      return;

    int i;

    if (uniform) {
      // Distribute to make all equal
      int max_size = 0;
      for( i=0; i < ncell; ++i )
	if( sizes[i] > max_size ) max_size = sizes[i];
      if (size >= max_size * ncell) {
	// Lucky, it will all fit
	for( i = 0; i < ncell; ++i )
	  sizes[i] = max_size;
	stot = max_size * ncell;
      } else {
	// Assign new weights, based on need
	wtot = 0;
	for (i=0; i<ncell; ++i) {
	  int d = max_size - sizes[i];
	  weights[i] = d;
	  wtot += d;
	}
      }
    }

    // Finally, distribute the excess by weight.
    if (wtot > 0) {
      // Distribute excess by weight
      final float excess = size - stot;
      final float step = excess/wtot;
      float e1 = 0, e2 = 0;
      for( i=0; i < ncell; ++i )
      {
	e2 += step*weights[i];
	sizes[i] += (int)e2 - (int)e1;
	e1 = e2;
      }
    }
  }

  // Given a child, cell position, and cell span, compute the size
  // and placement of the child.
  private void
  layoutChild(View child, int[] xs, int[] ys)
  {
      final LayoutParams lp = (LayoutParams) child.getLayoutParams();
      int excess;
      int x = xs[lp.gridx] + lp.leftMargin;
      int y = ys[lp.gridy] + lp.topMargin;
      if( lp.gravity == Gravity.NO_GRAVITY ) lp.gravity = gravity;

      final int hgravity = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
      final int width = computeCellWid(lp);
      int mw = child.getMeasuredWidth();
      final int cw = mw + lp.leftMargin + lp.rightMargin;

      // Correct for preferred fill & alignment
      //  hgravity:  object's gravity within the cell
      //  width: total width of the cell
      //  mw: child requested width ("measured width")
      //  cw: child requested width plus margins
      //  x: child left edge, after left margin
      //  lw: layout width -- the width that will be assigned

      if( hgravity != Gravity.FILL_HORIZONTAL && (excess = width - cw) > 0 )
      {
        switch( hgravity ) {
          default:
          case Gravity.LEFT: break;
          case Gravity.CENTER_HORIZONTAL: x += excess/2; break;
          case Gravity.RIGHT: x += excess; break;
        }
      } else {
	mw = width - (lp.leftMargin + lp.rightMargin);
      }

      final int vgravity = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
      final int height = computeCellHgt(lp);
      int mh = child.getMeasuredHeight();
      final int ch = mh + lp.topMargin + lp.bottomMargin;

      if( vgravity != Gravity.FILL_VERTICAL && (excess = height - ch) > 0 )
      {
        switch( vgravity ) {
          default:
          case Gravity.TOP: break;
          case Gravity.CENTER_VERTICAL: y += excess/2; break;
          case Gravity.BOTTOM: y += excess; break;
        }
      } else {
	mh = height - (lp.topMargin + lp.bottomMargin);
      }

      child.layout(x, y, x + mw, y + mh);
  }



  static public class LayoutParams extends ViewGroup.MarginLayoutParams {
    public int gridx;           // X position in the grid
    public int gridy;           // Y position in the grid
    public int colSpan;         // # columns it occupies
    public int rowSpan;         // # rows it occupies
    public int width;           // basic width of the view
    public int height;          // basic height of the view
    public int gravity = Gravity.NO_GRAVITY;
    public float weightx = 0;
    public float weighty = 0;

    public LayoutParams(Context c, AttributeSet attrs) {
	super(c, attrs);

	TypedArray a =
	  c.obtainStyledAttributes(attrs, R.styleable.Gridbox_Layout);
	gravity =
	  a.getInt(R.styleable.Gridbox_Layout_layout_gravity, Gravity.NO_GRAVITY);
	gridx = a.getInt(R.styleable.Gridbox_Layout_layout_gridx, -1);
	gridy = a.getInt(R.styleable.Gridbox_Layout_layout_gridy, -1);
	colSpan = a.getInt(R.styleable.Gridbox_Layout_layout_colSpan, 1);
	rowSpan = a.getInt(R.styleable.Gridbox_Layout_layout_rowSpan, 1);
	weightx = a.getFloat(R.styleable.Gridbox_Layout_layout_weightx, 0);
	weighty = a.getFloat(R.styleable.Gridbox_Layout_layout_weighty, 0);
    }

    public LayoutParams(ViewGroup.LayoutParams p) {
      super(p);
    }
  }


  /*
  static private final String decodeSpec(int spec) {
    int mode = MeasureSpec.getMode(spec);
    int wid = MeasureSpec.getSize(spec);
    String t;
    switch( mode ) {
      case MeasureSpec.AT_MOST: t = "A"; break;
      case MeasureSpec.EXACTLY: t = "E"; break;
      default:
      case MeasureSpec.UNSPECIFIED: t = "U"; break;
    }
    return t+wid;
  }
  */
}
