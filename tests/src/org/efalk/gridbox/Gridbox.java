/**
 * $Id: Gridbox.java,v 1.2 2009-11-17 17:38:41 falk Exp $
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
import java.lang.Math;
import java.lang.System;

import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import android.util.Log;

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
  // needs_layout = true;

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
    // TODO
  }

  /**
   * Add a child, specifying size and location within the grid.
   */
  public void addView(View child, int width, int height, int col, int row) {
    super.addView(child, width, height);
    // TODO
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int wid = View.MeasureSpec.getSize(widthMeasureSpec);
    int hgt = View.MeasureSpec.getSize(heightMeasureSpec);
    final int num_children = getChildCount();

    if( num_children <= 0 ) {
      setMeasuredDimension(0, 0);
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

    // Find out how large children would like to be
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

	// Ask child how much space it wants.  "Padding" argument is not
	// accurate, but will do for now; we'll be correcting later.
	measureChildWithMargins(child,
		widthMeasureSpec, 0, heightMeasureSpec, 0);
      }
    }

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

    if( force_uniform_width )
      force_uniform(max_wids);

    if( force_uniform_height )
      force_uniform(max_hgts);

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

    final int hpad = getPaddingLeft() + getPaddingRight();
    final int vpad = getPaddingTop() + getPaddingBottom();

    wid = getSize(total_wid + hpad, total_weightx, widthMeasureSpec);
    hgt = getSize(total_hgt + vpad, total_weighty, heightMeasureSpec);
    setMeasuredDimension(wid, hgt);

    // Step 5: Compute the sizes
    System.arraycopy(max_wids, 0, wids, 0, wids.length);
    distributeExcess(0, ncol, wid - hpad, wids, total_wid,
			weightx, total_weightx);

    // Same again, for heights
    System.arraycopy(max_hgts, 0, hgts, 0, hgts.length);
    distributeExcess(0, nrow, hgt - vpad, hgts, total_hgt,
			weighty, total_weighty);


    // Step 6: Make a second pass, tell children the actual size they got
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
	  child.measure(
	    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
	    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
	}
      }
    }
  }

  static private final int
  getSize(int size, float weight, int spec) {
    int mode = View.MeasureSpec.getMode(spec);
    int wid = View.MeasureSpec.getSize(spec);
    switch( mode ) {
      default:
      case View.MeasureSpec.EXACTLY: return wid;
      case View.MeasureSpec.UNSPECIFIED: return size;
      case View.MeasureSpec.AT_MOST:
	if( weight > 0 || size > wid) return wid;
	return size;
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b)
  {
      final int count = getChildCount();
      int i;
      int x,y;

      // TODO: should we do another layout pass just in case the
      // value are different from the onMeasure() pass?  Nobody else does.

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

      ncol = nrow = 0;
      max_col_span = max_row_span = 0;
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

  // Find out how many cells wide and high this grid will be.
  // Assign grid locations where needed.
  private void countCells() {
    // TODO: can we skip this if no children have been added or changed
    // since the last call?
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
  }



  // Utility:  Force all items in sizes[] to be the same
  static private void force_uniform(int[] sizes) {
    int max_size = 0;
    int i, n = sizes.length;
    for( i = 0; i < n; ++i )
      if( sizes[i] > max_size ) max_size = sizes[i];
    for( i = 0; i < n; ++i )
      sizes[i] = max_size;
  }



        // PRIVATE ROUTINES


  // Given a gridbox & child, compute the current size of
  // the cell(s) occupied by the child.

  private final int computeCellWid(final LayoutParams lp)
  {
    int x, i, wid;
    x = lp.gridx;
    for(wid=0, i=0; i < lp.colSpan; ++i)
      wid += wids[x++];
    return wid;
  }

  private final int computeCellHgt(final LayoutParams lp)
  {
    int y, i, hgt;
    y = lp.gridy;
    for(hgt=0, i=0; i < lp.rowSpan; ++i)
      hgt += hgts[y++];
    return hgt;
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


  // Utility: distribute excess space across a number of cells.
  // weight of the specified child.
  private static void
  distributeExcess(int idx, int ncell, int size, int[] sizes, int stot,
	float[] weights, float wtot)
  {
    if( idx < 0 || idx + ncell > sizes.length ) {
      Log.e(TAG, "distributeExcess: idx="+idx + ", ncell="+ncell +
        ", length="+sizes.length);
      return;
    }

    int i;

    // Is there excess size to distribute?
    if( size > stot && wtot > 0 )
    {
      final float excess = size - stot;
      final float step = excess/wtot;
      float e1 = 0, e2 = 0;

      for( i=0; i < ncell; ++i )
      {
        e2 += step*weights[idx+i];
        sizes[idx+i] += (int)e2 - (int)e1;
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
      int width = computeCellWid(lp);
      final int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;

      // Correct for preferred fill & alignment

      if( hgravity != Gravity.FILL_HORIZONTAL && (excess = width - cw) > 0 )
      {
        switch( hgravity ) {
          default:
          case Gravity.LEFT: break;
          case Gravity.CENTER_HORIZONTAL: x += excess/2; break;
          case Gravity.RIGHT: x += excess; break;
        }
        width = cw;
      }

      final int vgravity = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
      int height = computeCellHgt(lp);
      int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

      if( vgravity != Gravity.FILL_VERTICAL && (excess = height - ch) > 0 )
      {
        switch( vgravity ) {
          default:
          case Gravity.TOP: break;
          case Gravity.CENTER_VERTICAL: y += excess/2; break;
          case Gravity.BOTTOM: y += excess; break;
        }
        height = ch;
      }

      child.layout(x, y, x + width, y + height);
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
}
