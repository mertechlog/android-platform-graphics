# Graphics Geometry Terminology

## Objective

Understand how Android graphics describes **where content is, how large it is, what part of it is visible, and how it is transformed**.

This page focuses on the geometry information used when a graphical object moves from its source buffer to a destination display.

The key question is:

> **How does Android describe the shape, position, visible region, and transformation of graphical content?**

---

# 1. One Reference Model

Use this model throughout the graphics study:

```text
                 SOURCE
                   │
                   │  Source Buffer
                   │
                   ▼
            ┌───────────────┐
            │ Source Crop   │
            └───────┬───────┘
                    │
                    │ Transform
                    │ Scale / Rotate / Translate
                    ▼
            ┌───────────────┐
            │ Destination   │
            │ Frame         │
            └───────┬───────┘
                    │
                    ▼
                 DISPLAY
```

The source describes **what pixels are selected**.

The destination describes **where those pixels appear**.

The transform describes **how those pixels are changed while mapping from source to destination**.

---

# 2. Coordinate System

A coordinate system defines how positions are expressed.

For a typical 2D graphics coordinate system:

```text
(0,0) ───────────────────────────────► X
  │
  │
  │
  │
  ▼
  Y
```

Usually:

* `X` increases toward the right.
* `Y` increases toward the bottom.
* The origin is `(0,0)`.

For a display:

```text
             X
             ───────────────────►

        (0,0)
          ┌──────────────────────┐
          │                      │
          │       DISPLAY        │
          │                      │
          │                      │
          └──────────────────────┘
                             
          │
          │
          ▼
          Y
```

However, Android graphics contains **multiple coordinate spaces**.

For example:

```text
Display Space
      │
      ▼
Window Space
      │
      ▼
Layer Space
      │
      ▼
Buffer / Content Space
```

Do not assume that a coordinate such as `(100,100)` always refers to the same physical location.

Its meaning depends on the coordinate space in which it is expressed.

---

# 3. Coordinate Spaces

A graphical object can have geometry relative to different coordinate systems.

Common conceptual spaces include:

### Display Space

Coordinates relative to the display.

Example:

```text
Display = 1920 × 1080

Point:
(320,180)
```

means 320 pixels from the left and 180 pixels from the top of the display.

### Window Space

Coordinates relative to a window.

For example:

```text
Window:
1280 × 720

Point:
(100,50)
```

means 100 pixels from the left and 50 pixels from the top of that window.

### Buffer Space

Coordinates relative to the graphical buffer.

Example:

```text
Buffer:
1280 × 720

Point:
(500,300)
```

identifies a location inside that buffer.

### Layer Space

Coordinates associated with the layer's composition geometry.

A layer can have:

* position
* size
* crop
* transform
* alpha
* Z-order

The exact coordinate conversion depends on the layer hierarchy and transforms applied to it.

---

# 4. Bounds

**Bounds** describe the rectangular extent of an object.

A rectangle is commonly represented as:

```text
[left, top, right, bottom]
```

For example:

```text
[320, 180, 1600, 900]
```

represents:

```text
             320                 1600
              │                    │
              ▼                    ▼
          ┌──────────────────────────┐ 180
          │                          │
          │          CONTENT         │
          │                          │
          │                          │
          └──────────────────────────┘ 900
```

The important point is that:

```text
left   = 320
top    = 180
right  = 1600
bottom = 900
```

Width:

```text
right - left
= 1600 - 320
= 1280
```

Height:

```text
bottom - top
= 900 - 180
= 720
```

Therefore:

```text
Bounds = [320,180,1600,900]

Width  = 1280
Height = 720
```

---

# 5. Position

**Position** describes where an object is located.

For example:

```text
Position = (320,180)
```

can mean that the object's top-left corner is located at:

```text
X = 320
Y = 180
```

on the relevant coordinate space.

Position does not necessarily describe the object's size.

```text
Position:
(320,180)

Size:
1280 × 720
```

Together they describe:

```text
┌──────────────────────────────┐
│                              │
│          1280 × 720          │
│                              │
└──────────────────────────────┘
        ▲
        │
     (320,180)
```

---

# 6. Size

**Size** describes the dimensions of an object.

```text
Width  = 1280
Height = 720
```

Therefore:

```text
Size = 1280 × 720
```

Position and size are different concepts.

```text
Position = where
Size     = how large
```

For a rectangle:

```text
left = x
top  = y

right  = x + width
bottom = y + height
```

Conceptually:

```text
right  = left + width
bottom = top  + height
```

---

# 7. Bounds vs Position + Size

These are two ways of describing the same rectangular geometry.

Example:

```text
Position = (320,180)
Size     = 1280 × 720
```

produces:

```text
Bounds = [320,180,1600,900]
```

Because:

```text
right  = 320 + 1280 = 1600
bottom = 180 + 720   = 900
```

So:

```text
Position + Size
        │
        ▼
      Bounds
```

Bounds are especially useful because they describe the complete rectangle directly.

---

# 8. Crop

A **crop** selects only a specific region of a source.

Suppose the source buffer is:

```text
1920 × 1080
```

and we only want:

```text
x = 200 ... 1200
y = 100 ... 700
```

The selected region can conceptually be represented as:

```text
Source Crop =
[200,100,1200,700]
```

Visually:

```text
┌──────────────────────────────────────┐
│                                      │
│      ┌──────────────────────┐        │
│      │                      │        │
│      │     CROPPED AREA     │        │
│      │                      │        │
│      └──────────────────────┘        │
│                                      │
└──────────────────────────────────────┘
```

Crop answers:

> **Which part of the source should be used?**

Crop does not answer where that content appears on the display.

---

# 9. Clip

**Clip** limits the region that is allowed to remain visible.

Conceptually:

```text
Content
   │
   ▼
┌─────────────────────────┐
│       Clip Region       │
│                         │
│     visible content     │
│                         │
└─────────────────────────┘
```

Anything outside the clipping region is not visible through that clipped region.

The important distinction is:

```text
Crop = select source content

Clip = restrict visible output
```

They can produce visually similar results, but they operate conceptually at different stages.

---

# 10. Source Crop

A **source crop** defines the portion of the source buffer that participates in composition.

Example:

```text
Source Buffer:
1920 × 1080

Source Crop:
[320,180,1600,900]
```

This selects a:

```text
1280 × 720
```

region from the source.

```text
SOURCE BUFFER
1920 × 1080

┌────────────────────────────────────────┐
│                                        │
│       ┌──────────────────────┐         │
│       │                      │         │
│       │    SOURCE CROP       │         │
│       │      1280 × 720      │         │
│       │                      │         │
│       └──────────────────────┘         │
│                                        │
└────────────────────────────────────────┘
```

Source crop answers:

> **Which part of the source buffer should be displayed?**

---

# 11. Destination Frame

The **destination frame** describes where the selected source content should appear in the destination coordinate space.

For example:

```text
Destination Frame:
[320,180,1600,900]
```

on a:

```text
1920 × 1080
```

display.

```text
DISPLAY 1920 × 1080

┌──────────────────────────────────────────────┐
│                                              │
│       ┌──────────────────────────────┐       │
│       │                              │       │
│       │       DESTINATION FRAME      │       │
│       │          1280 × 720          │       │
│       │                              │       │
│       └──────────────────────────────┘       │
│                                              │
└──────────────────────────────────────────────┘
```

Destination frame answers:

> **Where should the selected content appear?**

---

# 12. Source Crop vs Destination Frame

This distinction is fundamental.

```text
SOURCE BUFFER
      │
      │ select region
      ▼
SOURCE CROP
      │
      │ transform / scale
      ▼
DESTINATION FRAME
      │
      ▼
DISPLAY
```

For example:

```text
Source buffer:
1920 × 1080

Source crop:
[320,180,1600,900]

Destination frame:
[0,0,1920,1080]
```

The selected `1280 × 720` region is scaled to fill the entire `1920 × 1080` destination.

Therefore:

```text
Source Crop       = what to use
Destination Frame = where to put it
```

---

# 13. Transform

A **transform** changes the geometric relationship between source and destination.

Common transformations include:

* translation
* scaling
* rotation
* combinations of these

Conceptually:

```text
Source
  │
  ▼
Transform
  │
  ├── Translate
  ├── Scale
  ├── Rotate
  └── Combined transform
  │
  ▼
Destination
```

A transform does not necessarily mean that pixels are rendered again.

Depending on the pipeline, geometry may be handled during composition.

---

# 14. Translation

**Translation** moves content without changing its size.

Suppose:

```text
Original position:
(0,0)
```

and we translate by:

```text
(+320,+180)
```

The new position becomes:

```text
(320,180)
```

Visually:

```text
Before:

┌───────────────┐
│ CONTENT       │
│               │
└───────────────┘


After translation:

              ┌───────────────┐
              │ CONTENT       │
              │               │
              └───────────────┘
              ▲
              │
           +320,+180
```

Translation changes:

```text
position
```

but does not inherently change:

```text
width
height
```

---

# 15. Scale

**Scale** changes the size of content.

Example:

```text
Original:
1280 × 720
```

Scale factor:

```text
2.0
```

Result:

```text
2560 × 1440
```

Conceptually:

```text
1280 × 720
      │
      │ scale ×2
      ▼
2560 × 1440
```

Scaling can be:

### Uniform

Same factor on both axes:

```text
scaleX = 2
scaleY = 2
```

### Non-uniform

Different factors:

```text
scaleX = 2
scaleY = 1.5
```

Non-uniform scaling can change the aspect ratio.

---

# 16. Rotation

**Rotation** changes the orientation of content.

Conceptually:

```text
0°
 │
 ▼
90°
 │
 ▼
180°
 │
 ▼
270°
```

For example:

```text
Before:

┌──────────────┐
│              │
│   CONTENT    │
│              │
└──────────────┘


After 90° rotation:

┌───────┐
│       │
│       │
│CONTENT│
│       │
│       │
└───────┘
```

Rotation can be performed around a defined pivot/origin.

This is important because rotation is not simply:

```text
width ↔ height
```

The complete geometric transformation also depends on the coordinate system and pivot.

---

# 17. Matrix

A **matrix** is a mathematical representation used to express geometric transformations.

For 2D graphics, transformations such as:

* translation
* scale
* rotation
* combinations of transformations

can be represented using matrices.

Conceptually:

```text
Point
  │
  ▼
┌──────────────┐
│ Transform    │
│   Matrix     │
└──────┬───────┘
       │
       ▼
Transformed Point
```

A transformation matrix allows multiple operations to be combined.

For example:

```text
Scale
  +
Rotate
  +
Translate
```

can be represented as one combined transformation.

The exact matrix representation depends on the graphics API and coordinate convention.

---

# 18. Alpha

**Alpha** represents transparency/opacity information.

Conceptually:

```text
Alpha = 1.0
```

means fully opaque.

```text
Alpha = 0.0
```

means fully transparent.

Intermediate values produce blending.

```text
1.0 ───────────── Fully opaque
 │
 │
0.5 ───────────── Partially transparent
 │
 │
0.0 ───────────── Fully transparent
```

Alpha is different from geometry.

For example:

```text
Position = (320,180)
Size     = 1280 × 720
Alpha    = 0.5
```

The object remains:

```text
1280 × 720
```

at:

```text
(320,180)
```

but is displayed with partial opacity.

---

# 19. Z-Order

**Z-order** determines the front-to-back ordering of overlapping graphical content.

Example:

```text
Z = 0   Background

Z = 1   Video

Z = 2   Dialog

Z = 3   Status Overlay
```

Conceptually:

```text
            FRONT
              ▲
              │
        ┌──────────────┐
        │   Overlay    │ Z=3
        ├──────────────┤
        │    Dialog    │ Z=2
        ├──────────────┤
        │    Video     │ Z=1
        ├──────────────┤
        │  Background  │ Z=0
        └──────────────┘
              │
              ▼
            BACK
```

If two layers overlap, their Z-order influences which one appears in front.

Z-order is therefore part of **composition geometry/state**, not pixel rendering itself.

---

# 20. Complete Geometry Example

Consider this display:

```text
Display:
1920 × 1080
```

An application has content:

```text
1280 × 720
```

We want the content centered on the display.

The required horizontal margin is:

```text
(1920 - 1280) / 2
= 320
```

The required vertical margin is:

```text
(1080 - 720) / 2
= 180
```

Therefore:

```text
Position = (320,180)
Size     = 1280 × 720
```

The destination frame becomes:

```text
[320,180,1600,900]
```

because:

```text
right  = 320 + 1280 = 1600
bottom = 180 + 720   = 900
```

The display therefore looks like:

```text
                    1920
        ◄──────────────────────────────►

        ┌────────────────────────────────────┐
        │                                    │
        │       320px top margin             │
        │                                    │
        │    ┌────────────────────────┐      │
        │    │                        │      │
        │    │                        │      │
 1080   │    │      APP CONTENT       │      │
        │    │       1280 × 720       │      │
        │    │                        │      │
        │    │                        │      │
        │    └────────────────────────┘      │
        │                                    │
        │       180px bottom margin          │
        │                                    │
        └────────────────────────────────────┘
```

Therefore:

```text
Display:
1920 × 1080

App:
1280 × 720

Destination:
[320,180,1600,900]
```

---

# 21. Mapping Source to Destination

Now consider the source buffer:

```text
Source Buffer:
1280 × 720
```

We want to display the complete buffer at:

```text
Destination:
[320,180,1600,900]
```

Therefore:

```text
Source Crop:
[0,0,1280,720]

Destination Frame:
[320,180,1600,900]
```

The mapping is:

```text
SOURCE SPACE                         DISPLAY SPACE

[0,0]                                [320,180]
   ┌────────────────┐                    ┌────────────────┐
   │                │                    │                │
   │  1280 × 720    │  ──────────────►   │  1280 × 720    │
   │                │                    │                │
   └────────────────┘                    └────────────────┘
[1280,720]                          [1600,900]
```

Because source and destination have the same size:

```text
1280 × 720
      ↓
1280 × 720
```

there is no scaling required.

Only translation into display coordinates is needed.

---

# 22. Geometry During Composition

When SurfaceFlinger/HWC composes layers, the system needs information such as:

```text
Layer
 ├── Buffer
 ├── Position
 ├── Size
 ├── Crop
 ├── Transform
 ├── Alpha
 ├── Z-order
 └── Visibility
```

Conceptually:

```text
                LAYER
                  │
        ┌─────────┴─────────┐
        │                   │
      CONTENT             STATE
        │                   │
      Buffer        ┌───────┼────────┐
                    │       │        │
                 Geometry  Alpha   Z-order
                    │
             ┌──────┼──────┐
             │      │      │
           Crop   Transform Position
```

This information tells the composition system how the layer should appear.

---

# 23. Geometry Does Not Mean Rendering

A critical distinction:

```text
Rendering
    │
    ▼
Creates graphical content
```

while:

```text
Geometry
    │
    ▼
Describes where/how that content appears
```

For example, an application may render:

```text
1280 × 720 image
```

The composition system can then display it:

```text
at position:
(320,180)

with alpha:
0.8

with rotation:
90°

with a particular Z-order
```

The geometry describes the presentation of the content.

It does not necessarily require the application to redraw the content.

---

# 24. Geometry and Composition

A useful mental model is:

```text
                  BUFFER
                    │
                    │ pixels
                    ▼
                  LAYER
                    │
          ┌─────────┼─────────┐
          │         │         │
        Crop    Transform   Alpha
          │         │         │
          └─────────┼─────────┘
                    │
                 Z-order
                    │
                    ▼
               COMPOSITION
                    │
                    ▼
                 DISPLAY
```

The buffer provides the graphical content.

The layer state describes how that content should participate in composition.

---

# 25. Crop vs Clip vs Destination

These three concepts are easy to confuse.

### Crop

Selects a region from the source.

```text
Source → Crop → Selected source region
```

### Clip

Restricts what portion remains visible.

```text
Content → Clip → Visible region
```

### Destination Frame

Defines where the resulting content is placed.

```text
Selected content → Destination Frame → Display location
```

Therefore:

```text
Crop      = WHAT part of source
Clip      = WHAT part may remain visible
Destination = WHERE it appears
```

---

# 26. Position vs Translation

These concepts are related but not identical.

**Position** describes the resulting location of an object in a coordinate space.

**Translation** is an operation that moves geometry.

For example:

```text
Original position:
(0,0)

Translation:
(+320,+180)

Resulting position:
(320,180)
```

Therefore:

```text
Translation = operation

Position = resulting location
```

---

# 27. Size vs Scale

Similarly:

```text
Size  = resulting dimensions
Scale = transformation applied to dimensions
```

Example:

```text
Original size:
1280 × 720

Scale:
2.0

Result:
2560 × 1440
```

So:

```text
Scale → changes size
```

but they should not be treated as synonyms.

---

# 28. Geometry Reference Table

| Term              | Meaning                                        | Main Question                                 |
| ----------------- | ---------------------------------------------- | --------------------------------------------- |
| Coordinate system | Space in which positions are expressed         | Relative to what?                             |
| Position          | Location of an object                          | Where?                                        |
| Size              | Width and height                               | How large?                                    |
| Bounds            | Rectangular extent                             | What rectangle?                               |
| Crop              | Selected source region                         | Which source pixels?                          |
| Clip              | Visible restriction                            | Which output region can remain visible?       |
| Source crop       | Region selected from source buffer             | Which source area?                            |
| Destination frame | Destination rectangle                          | Where should it appear?                       |
| Transform         | Geometric operation                            | How should geometry change?                   |
| Translation       | Moves geometry                                 | Where should it move?                         |
| Scale             | Changes dimensions                             | How much should it grow/shrink?               |
| Rotation          | Changes orientation                            | Which direction should it face?               |
| Matrix            | Mathematical representation of transformations | How are transformations represented/combined? |
| Alpha             | Opacity/transparency                           | How transparent?                              |
| Z-order           | Front/back ordering                            | Which layer is in front?                      |

---

# 29. One Unified Example

Take four layers:

```text
Background
Video
Dialog
Status Bar
```

Each layer can have different geometry.

```text
Background
    Position: (0,0)
    Size:     1920 × 1080
    Z:        0

Video
    Position: (320,180)
    Size:     1280 × 720
    Z:        1

Dialog
    Position: (600,300)
    Size:     720 × 400
    Z:        2

Status Bar
    Position: (0,0)
    Size:     1920 × 80
    Z:        3
```

Conceptually:

```text
                 DISPLAY
          1920 × 1080
┌──────────────────────────────────────┐
│ STATUS BAR                     Z=3   │
├──────────────────────────────────────┤
│                                      │
│          ┌──────────────────┐        │
│          │                  │        │
│          │      VIDEO       │ Z=1   │
│          │                  │        │
│          │    ┌────────┐    │        │
│          │    │ DIALOG │    │ Z=2   │
│          │    └────────┘    │        │
│          │                  │        │
│          └──────────────────┘        │
│                                      │
└──────────────────────────────────────┘
```

The composition system combines these layers according to their:

```text
Geometry
+
Transform
+
Alpha
+
Z-order
+
Visibility
```

The resulting composition becomes the frame presented to the display.

---

# 30. Important Mental Model

Keep these questions separate:

```text
Coordinate System
        │
        └── Relative to what?

Position
        │
        └── Where?

Size
        │
        └── How large?

Bounds
        │
        └── What rectangle?

Source Crop
        │
        └── Which source pixels?

Clip
        │
        └── Which region is allowed to remain visible?

Destination Frame
        │
        └── Where does it appear?

Transform
        │
        └── How is the geometry changed?

Alpha
        │
        └── How opaque is it?

Z-order
        │
        └── Which content is in front?
```

---

# 31. Final Reference Model

For Android graphics, think about geometry as a mapping problem:

```text
                 SOURCE BUFFER
                       │
                       │
                 Source Crop
                       │
                       ▼
                Selected Content
                       │
                       │
                 Transformation
                 ┌─────┼─────┐
                 │     │     │
               Scale Rotate Translate
                 └─────┼─────┘
                       │
                       ▼
               Destination Frame
                       │
                       │
                 Alpha / Z-order
                       │
                       ▼
                  Composition
                       │
                       ▼
                    Display
```

The most important distinction is:

```text
SOURCE CROP
    =
WHAT content is selected

DESTINATION FRAME
    =
WHERE that content appears

TRANSFORM
    =
HOW the content is geometrically mapped
```

---

# Summary

Android graphics geometry is fundamentally about describing and transforming **rectangles, coordinates, and relationships between source content and destination display space**.

The essential vocabulary is:

```text
Coordinate System → where coordinates are interpreted

Position          → where an object is

Size              → how large it is

Bounds            → rectangular extent

Crop              → which source region is selected

Clip              → which region may remain visible

Source Crop       → source-side selection

Destination Frame → destination-side placement

Transform         → geometric modification

Translation       → movement

Scale             → resizing

Rotation          → orientation change

Matrix            → mathematical transformation representation

Alpha             → opacity

Z-order            → front/back ordering
```

For the central example:

```text
Display:
1920 × 1080

Application:
1280 × 720

Position:
(320,180)

Destination Frame:
[320,180,1600,900]
```

the application content is centered because:

```text
Horizontal margin = 320
Vertical margin   = 180
```

The single most useful mental model is:

```text
Source Crop
     ↓
Transform
     ↓
Destination Frame
     ↓
Composition
     ↓
Display
```

This geometry model will be used later when studying **WindowManager → SurfaceControl → SurfaceFlinger → HWC**, where abstract window/layer state becomes actual display composition geometry.
