# Gridbox
A grid layout widget for Android. Like GridLayout, but written for Android 1.0, before GridLayout was available.

The Gridbox widget aligns its children in a rectangular array of cells.
Child widgets occupy a rectangular region of cells (default
size is 1x1).  A typical layout might look like this:

```
     +-------+-------+---+--------------------------+
     | cell  | cell  |   |                          |
     |-------+-------| c |                          |
     | cell  | cell  | e |                          |
     |-------+-------| l |          cell            |
     | cell  | cell  | l |                          |
     |-------+-------|   |                          |
     | cell  | cell  |   |                          |
     |-------+-------+---+--------------------------|
     |         cell      |           cell           |
     +-------------------+--------------------------+
```

In addition, child widgets may specify a margin, and weights which
determine how they are resized if the parent widget is resized.

# To use Gridbox

Set up Gridbox as an Android library or simply copy it into your
application's source base.

In your layout.xml files, add this to your top-level tag:

```
xmlns:gridbox="http://schemas.android.com/apk/res/org.com.example"
```

Use this tag (example) in your layout.xml file:

```
  <org.efalk.gridbox.Gridbox
      android:id="@+id/buttonContainer"
      android:background="@color/bgColor"
      android:layout_width="fill_parent"
      android:layout_height="fill_parent"
      gridbox:gravity="fill"
      gridbox:force_uniform_width="true"
      >
```

Child widges of a Gridbox should contain gridbox-specific attributes:

```
    <Button android:id="@+id/verticalCell"
        android:text="@string/text1"
        gridbox:layout_gridx="2" gridbox:layout_gridy="0"
        gridbox:layout_rowSpan="4"
        gridbox:layout_weighty="1"
        />
```

## Gridbox attributes

Name | What
---- | ----
gridbox:gravity | Default child gravity
gridbox:inner_margin | Margin between children
gridbox:force_uniform_width | boolean; all columns same width
gridbox:force_uniform_height | boolean; all rows same height

Note: the force_uniform_* attributes work by assigning
excess space to columns/rows in order to achieve a
uniform size. If there is not enough excess space to
accomplish this, Gridbox does the best it can.  Otherwise,
all cells are made the same size, and then any remaining
excess is distributed by weight.  If you really want a
uniform size, then the weights should all be the same.

## Gridbox child layout attributes:

Name | What
---- | ----
gridbox:layout_gridx | X position in the grid
gridbox:layout_gridy | Y position in the grid
gridbox:layout_colSpan | # columns it occupies
gridbox:layout_rowSpan | # rows it occupies
android:layout_width | width of the view
android:layout_height | height of the view
android:layout_margin | space around the view
android:layout_marginBottom |
android:layout_marginTop |
android:layout_marginLeft |
android:layout_marginRight |
gridbox:layout_gravity | How child is placed in cell

Gravity can be a combination of:
**left**, **right**, **center_horizontal**, **fill_horizontal**
**top**, **bottom**, **center_vertical**, **fill_vertical**
**center**, **fill**

If there is extra space to allocate, the following attributes become
meaningful:

Name | What
---- | ----
gridbox:layout_weightx | How much of the excess horizontal/
gridbox:layout_weighty | vertical space the cell should get

Child grid positions do not need to be specified in any particular order;
it's perfectly acceptable to lay out by rows, by columns, or in any
other order you choose.  Placing two child widgets in the same cell will
cause one to overlay the other.

If not specified, the X and Y position of a cell in the grid default
to one cell to the right of the previous cell.

Column and row spans default to 1.

The widget sizes should never be specified as **fill_parent** -- use
wrap_content or an explicit value instead.  If you want a child widget
to grow to fill its cell in the table, set its layout_gravity parameter
to FILL_HORIZONTAL and/or FILL_VERTICAL.

The weight values determine how much of the extra space is assigned to
the row or column.  The child widget won't actually occupy the entire
cell unless you set gravity to FILL_HORIZONTAL and/or FILL_VERTICAL.

If all weights are zero, excess space is distributed evenly, as if all
weights were one.

Note:  Each column or row is assigned the maximum weightx/weighty value
of all of its cells.  This means that you can often assign a weight to
just one item to get the desired effect.


