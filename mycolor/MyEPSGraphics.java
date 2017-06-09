package mycolor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.RenderableImage;

import java.io.BufferedWriter;
import java.io.IOException;

import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;

import java.util.Hashtable;
import java.util.Map;

/**
 * This class servrs to save image in Encapsulated Post Script format.
 * Using of this class is simple and usual:
 *
 *  Component comp is the component image on which have to be saved.
 *  MyEPSGraphics mg = new MyEPSGraphics(comp.width, comp.height,
 *                "My application name", "Copyright text",
 *                new BufferedWriter(new FileWriter("file name.eps")));
 *  comp.paint(mg);
 *  mg.close();
 *
 *  Methods of creation and closing of MyEPSGraphics create, write and
 *  close file (or other writer). These methods can throw IOExeption.
 *
 *  To use debug information is nesessary to switch on boolean field inDebug
 *  in EPSStorage. To do this method setDebug(boolean debug) is added.
 *
 *  Using of short spelling PS comands now is not optional. The usage of
 *  such trick help to obtain more short files. Since list of shortage command
 *  includes in file I decide use abbreviated and redefined commands permanently.
 *  The real list of short command is described in EPSStorage.
 *  To correct using of short command each added to storage string analised by
 *  following rule:
 *     1. the last part of string which is separated by space is found.
 *     2. if the string does not contain space then part is equal to whole string
 *     3. The found part is cheched in map of abbreviation.
 *  If you use the comman which located not in the end of string you must
 *  call setCommand method of storage to check this command. If you do not call
 *  setCommand this command can be not included in the result file.
 *
 *  There are two mode to work with fonts in this class:
 *    1. Standard mode is using Java defined Postscript names of fonts. The real
 *       existanse of fonts on computer where file will be used in this case is the
 *       problem of user. This mode can be set by using <code>encapsulateFonts(false)</code>.
 *    2. Encapsulate font to EPS file. This mode can be set by using
 *       <code>encapsulateFonts(true)</code>. In this mode all used fonts
 *       are inserted to EPS file and this file can be used without any
 *       problem but size of file in this case will be greater.
 *
 *  Since the PS does not work with transparency I exclude composite. But
 *  I like to use Alpha to change colours. For this pupose I proseed alpha
 *  composition of new colour with background colour in setColor method.
 *  To realize full transparency algorithm we must create the png (bmp, gif
 *  etc.) image and then comvert it to EPS. The result of such work will
 *  not be a pure EPS file and convertors of png, jpeg and bmp to EPS exist.
 *
 *  Sinnce the PS does not work with gradient painting I not support Paint.
 *  It is possible to implement this property latter when I understand the
 *  easy and clear way to do it.
 *
 *  Now not ready and not tested
 *  drawImage - usage of more compact binary data record
 *  Work with fonts - using of non standard fonts for PS.
 *  
 * @author Evgeny Mirkes (University of Leicester, UK)
*/
public class MyEPSGraphics extends Graphics2D {


    public static final int BLACK_AND_WHITE = 0;
    public static final int GRAYSCALE = 1;
    public static final int RGB = 2;

    private Color col;
    private Color bgCol;
    private Paint paint;
    private BasicStroke stroke;
    private Font font;
    private Shape clip;
    private AffineTransform transform;
    private int colorDepth;

    private EPSStorage storage;
    private MyEPSGraphics parent = null;

    private static FontRenderContext fontRenderContext = new FontRenderContext(null, false, true);

    //Debug

    /**
     * These two field is serves to debug this graphics class.
     */
    private static int newNum = 0;
    private int myNum;

    /**
     * Create a new copy of graphics.
     * For part of fields <code>(clip, transform)</code> new clones are created
     * @param meg is parent MyEPSGraphics oblect
     */
    public MyEPSGraphics(MyEPSGraphics meg) {
        super();
        myNum = newNum++;
        parent = meg;
        col = meg.col;
        bgCol = meg.bgCol;
        paint = meg.paint;
        stroke = meg.stroke;
        font = meg.font;
        clip = new Area(meg.clip);
        transform = new AffineTransform(meg.transform);
        colorDepth = meg.colorDepth;
        storage = meg.storage;
        storage.debug("Creation of " + myNum + " by copying of " + meg.getNum());
        //This call is necessary to register this object in storage
        storage.append(this, null);
    }


    /**
     * Create the first (main) <code>MyEPSGraphics</code> object.
     * All other examplar of this class are created automatically by <code>Component</code>.
     * @param width is the width of canvas
     * @param height is the height of canvas
     * @param title is the title of EPS file
     * @param copyright is the content of Copyright comment of EPS file
     * @param bw is <code>BufferedWriter</code> to write EPS file
     * @throws IOException - if an I/O error occures
     */
    public MyEPSGraphics(float width, float height, String title, String copyright,
                         BufferedWriter bw) throws IOException {
        super();

        myNum = newNum++;

        storage = new EPSStorage(width, height, title, copyright, bw);
        bgCol = Color.WHITE;
        clip = new Rectangle2D.Float(0, 0, width, height);
        transform = new AffineTransform();
        colorDepth = RGB;
        col = Color.BLACK;
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        font=null;
        setFont(g.getFont());
        setStroke(new BasicStroke());

        storage.debug("Creation " + myNum);
    }

    //debug methods

    /**
     * Set the specified debbuger state.
     * @param debug is the state to set
     */
    public void setDebug(boolean debug) {
        storage.setDebug(debug);
    }

    /**
     * Debug method.
     * @return internal number of this copy of <code>MyEPSGraphics</code>.
     */
    public int getNum() {
        return myNum;
    }

    //My own methods

    /**
     * The parent field is used by <code>EPSStorage</code> class to check the
     * order in graph stack.
     * @return the parent <code>MyEPSGraphics</code> object.
     */
    protected MyEPSGraphics getParent() {
        return parent;
    }

    /**
     * Finalise the work of <code>MyEPSGraphics</code> and linked <code>EPSStorage</code>.
     * The <code>MyEPSGraphics</code> and all it's parents and descendants will be unworkable
     * after calling of this method!
     * @throws IOException - if an I/O error occures
     */
    public void close() throws IOException {
        storage.debug(this.getNum() + " Close");
        storage.close(this);
    }

    /**
     * Convert integer to two symbols hexadecimal representation.
     * @param n is the number to convert to hexadecimal represintation
     * @return the two digital hexadecimal represintation
     */
    private String toHexString(int n) {
        String result = Integer.toHexString(n);
        while (result.length() < 2) {
            result = "0" + result;
        }
        return result;
    }

    /**
     * Service method to draw or fill shape.
     * @param s is shape to draw or fill
     * @param action is the name of command. Name must be one of the following
     * <code>st</code> (stroke) or <code>fl</code> (fill).
     */
    private void draw(Shape s, String action) {
        storage.debug(this.getNum() + " Draw shape");
        //Not work with empty shape
        if (s == null)
            return;
        storage.append(this, "np");
        int type = 0;
        float[] coords = new float[6];
        PathIterator it = s.getPathIterator(null);
        //To remember start point of next segment. It is necessary to change quadric to cube segment.
        float x0 = 0;
        float y0 = 0;
        while (!it.isDone()) {
            type = it.currentSegment(coords);

            if (type == PathIterator.SEG_CLOSE) {
                // Close path by PS command
                storage.append(this, "cp");
            } else if (type == PathIterator.SEG_CUBICTO) {
                //Cubic line
                storage.append(this, coords[0] + " " + coords[1] + " " + coords[2] + //
                        " " + coords[3] + " " + coords[4] + " " + coords[5] + " c");
                x0 = coords[4];
                y0 = coords[5];
            } else if (type == PathIterator.SEG_LINETO) {
                //Linear segment
                storage.append(this, coords[0] + " " + coords[1] + " l");
                x0 = coords[0];
                y0 = coords[1];
            } else if (type == PathIterator.SEG_MOVETO) {
                //Linear segment without line
                storage.append(this, coords[0] + " " + coords[1] + " m");
                x0 = coords[0];
                y0 = coords[1];
            } else if (type == PathIterator.SEG_QUADTO) {
                //Quadric segment. There is no quadric comand in PS
                // Transform one calibrate point to two calibrate points for the cubic
                // Bezier polinom which draw exactly the same curve
                float q1x = (x0 + 2 * coords[0]) / 3;
                float q1y = (y0 + 2 * coords[1]) / 3;
                float q2x = (2 * coords[0] + coords[2]) / 3;
                float q2y = (2 * coords[1] + coords[3]) / 3;
                storage.append(this,
                               q1x + " " + q1y + " " + q2x + " " + q2y + " " + coords[2] + " " + coords[3] + " c");
                x0 = coords[2];
                y0 = coords[3];
            }
            it.next();
        }
        storage.append(this, action);
    }

    /**
     * Set the one of the possible font work mode:
     *    1. Standard mode is using Java defined Postscript names of fonts. The real
     *       existanse of fonts on computer where file will be used in this case is the
     *       problem of user. This mode can be set by using <code>encapsulateFonts(false)</code>.
     *    2. Encapsulate font to EPS file. This mode can be set by using
     *       <code>encapsulateFonts(true)</code>. In this mode all used fonts
     *       are inserted to EPS file and this file can be used without any
     *       problem but size of file in this case will be greater.
     *
     * @param ef is true for encapsulated fonts and fale otherwise
     */
    public void encapsulateFonts(boolean ef) {
        storage.encapsulateFonts(ef);
    }
    
    //Graphics2D methods

    @Override
    public void draw(Shape s) {
        storage.debug(this.getNum() + " Draw shape base");
        draw(s, "st");
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        storage.debug(this.getNum() + " Draw Image 1");
        if (xform != null && !xform.isIdentity()) {
            storage.append(this, "gs");
            double[] matr = new double[6];
            xform.getMatrix(matr);
            storage.append(this, "[" + matr[0] + " " + matr[1] + " " + matr[2] + " " + matr[3] + " " + matr[4] + //
                    " " + matr[5] + "] cc");
        }
        boolean st = drawImage(img, 0, 0, obs);
        if (xform != null && !xform.isIdentity()) {
            storage.append(this, "gr");
        }
        return st;
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        storage.debug(this.getNum() + " Draw Image 2");
        BufferedImage img1 = op.filter(img, null);
        drawImage(img1, AffineTransform.getTranslateInstance(x, y), null);
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        storage.debug(this.getNum() + " Draw drawRenderedImage");
        Hashtable properties = new Hashtable();
        String[] names = img.getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            properties.put(names[i], img.getProperty(names[i]));
        }
        ColorModel cm = img.getColorModel();
        WritableRaster wr = img.copyData(null);
        BufferedImage img1 = new BufferedImage(cm, wr, cm.isAlphaPremultiplied(), properties);
        AffineTransform at = AffineTransform.getTranslateInstance(img.getMinX(), img.getMinY());
        at.preConcatenate(xform);
        drawImage(img1, at, null);
    }

    @Override
    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
        storage.debug(this.getNum() + " Draw drawRenderableImage");
        drawRenderedImage(img.createDefaultRendering(), xform);
    }

    @Override
    public void drawString(String str, int x, int y) {
        storage.debug(this.getNum() + " Draw drawString int");
        float x1 = x, y1 = y;
        drawString(str, x1, y1);
    }

    @Override
    public void drawString(String s, float x, float y) {
        storage.debug(this.getNum() + " Draw drawString float");
        if (s != null && !s.isEmpty()) {
            AttributedString as = new AttributedString(s);
            as.addAttribute(TextAttribute.FONT, font);
            drawString(as.getIterator(), x, y);
        }
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        storage.debug(this.getNum() + " Draw drawString attr iter 1");
        float x1 = x, y1 = y;
        drawString(iterator, x1, y1);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        storage.debug(this.getNum() + " Draw drawString attr iter 2");
        //Set fonts using
        EPSStorage.FontInf fi = storage.getFontInf(font);
        //Move to target point save position and turn over the graphics (y axis) because we turn over it initialy
        StringBuffer buf = new StringBuffer(100);
        buf.append("(");
        for (char ch = iterator.first(); ch != CharacterIterator.DONE; ch = iterator.next()) {
            //For the PS symbols "(" and ")" are special and must be preceded by "\"
            if (ch == '(' || ch == ')') {
                buf.append('\\');
            }
            buf.append(ch);
            fi.addSymbols(ch);
        }
        buf.append(") " + x + " " + y + " s");

        storage.append(this, buf.toString());
        //draw the string and restore graphics to remove the turning over the y axis!
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
        storage.debug(this.getNum() + " Draw drawGlyphVector");
        Shape shape = g.getOutline(x, y);
        draw(shape, "fl");
    }

    @Override
    public void fill(Shape s) {
        storage.debug(this.getNum() + " Draw fill");
        draw(s, "fl");
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        storage.debug(this.getNum() + " Draw hit");
        return s.intersects(rect);
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        storage.debug(this.getNum() + " Draw GraphicsConfiguration");
        GraphicsConfiguration gc = null;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();
        for (int i = 0; i < gds.length; i++) {
            GraphicsDevice gd = gds[i];
            GraphicsConfiguration[] gcs = gd.getConfigurations();
            if (gcs.length > 0) {
                return gcs[0];
            }
        }
        return gc;
    }

    @Override
    public void setComposite(Composite comp) {
        storage.debug(this.getNum() + " Draw setComposite");
    }

    //Now we work with Color only!

    @Override
    public void setPaint(Paint paint) {
        storage.debug(this.getNum() + " Draw setPaint");
        this.paint = paint;
        if (paint instanceof Color) {
            setColor((Color)paint);
        }
    }

    @Override
    public void setStroke(Stroke s) {
        storage.debug(this.getNum() + " Draw setStroke");
        if (s instanceof BasicStroke) {
            if (stroke != null && stroke.equals(s))
                return;
            stroke = (BasicStroke)s;
            StringBuffer buf = new StringBuffer(100);
            float miterLimit = stroke.getMiterLimit();
            if (miterLimit < 1.0f) {
                miterLimit = 1;
            }

            buf.append("[ ");
            float[] dashArray = stroke.getDashArray();
            if (dashArray != null) {
                for (int i = 0; i < dashArray.length; i++) {
                    buf.append((dashArray[i]) + " ");
                }
            }
            buf.append("] 0 ");
            buf.append(stroke.getEndCap() + " ");
            buf.append(stroke.getLineJoin() + " ");
            buf.append(miterLimit + " ");
            buf.append(stroke.getLineWidth() + " ss");
            storage.append(this, buf.toString());
        }
    }

    @Override
    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
        storage.debug(this.getNum() + " Draw setRenderingHint");
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key hintKey) {
        storage.debug(this.getNum() + " Draw getRenderingHint");
        return null;
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        storage.debug(this.getNum() + " Draw setRenderingHints");
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        storage.debug(this.getNum() + " Draw addRenderingHints");
    }

    @Override
    public RenderingHints getRenderingHints() {
        storage.debug(this.getNum() + " Draw getRenderingHints");
        RenderingHints rh = null;
        return rh;
    }

    @Override
    public void translate(int x, int y) {
        storage.debug(this.getNum() + " Draw translate int "+x+" "+y);
        if (x == 0 && y == 0) {
            storage.debug("% ignored");
            return;
        }
        double x1 = x, y1 = y;
        translate(x1, y1);
    }

    @Override
    public void translate(double tx, double ty) {
        storage.debug(this.getNum() + " Draw translate double");
        if (tx == 0 && ty == 0) {
            storage.debug("% ignored");
            return;
        }
        storage.append(this, tx + " " + ty + " tr");
        AffineTransform at = AffineTransform.getTranslateInstance(tx, ty);
        transform.concatenate(at);
        try {
            clip = at.createInverse().createTransformedShape(clip);
        } catch (NoninvertibleTransformException e) {
            ;
        }
    }

    @Override
    public void rotate(double theta) {
        storage.debug(this.getNum() + " Draw rotate theta");
        rotate(theta, 0, 0);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        storage.debug(this.getNum() + " Draw rotate Theta x y");
        //In accordance with documentations
        if (x != 0 || y != 0)
            storage.append(this, x + " " + y + " tr");
        storage.append(this, (theta * 180 / Math.PI) + " r");
        if (x != 0 || y != 0)
            storage.append(this, (-x) + " " + (-y) + " tr");
        AffineTransform at = AffineTransform.getRotateInstance(theta, x, y);
        transform.concatenate(at);
        try {
            clip = at.createInverse().createTransformedShape(clip);
        } catch (NoninvertibleTransformException e) {
            throw new MyEPSException("Added transform is not invertable:" + e.getMessage());
        }
    }

    @Override
    public void scale(double sx, double sy) {
        storage.debug(this.getNum() + " Draw scale");
        storage.append(this, sx + " " + sy + " sc");
        AffineTransform at = AffineTransform.getScaleInstance(sx, sy);
        transform.concatenate(at);
        try {
            clip = at.createInverse().createTransformedShape(clip);
        } catch (NoninvertibleTransformException e) {
            throw new MyEPSException("Added transform is not invertable:" + e.getMessage());
        }
    }

    @Override
    public void shear(double shx, double shy) {
        storage.debug(this.getNum() + " Draw shear");
        //In accordance with documentations
        storage.append(this, "[1 " + shx + " " + shy + " 1 0 0] cc");
        AffineTransform at = AffineTransform.getShearInstance(shx, shy);
        transform.concatenate(at);
        try {
            clip = at.createInverse().createTransformedShape(clip);
        } catch (NoninvertibleTransformException e) {
            throw new MyEPSException("Added transform is not invertable:" + e.getMessage());
        }
    }

    @Override
    public void transform(AffineTransform Tx) {
        storage.debug(this.getNum() + " Draw transform");
        double[] matr = new double[6];
        Tx.getMatrix(matr);
        storage.append(this, "[" + matr[0] + " " + matr[1] + " " + matr[2] + " " + matr[3] + " " + matr[4] + //
                " " + matr[5] + "] cc");
        transform.concatenate(Tx);
        try {
            clip = Tx.createInverse().createTransformedShape(clip);
        } catch (NoninvertibleTransformException e) {
            throw new MyEPSException("Added transform is not invertable:" + e.getMessage());
        }
    }


    /**
     * For this realization we use following ruler:
     * setTransform can be used for the saved transform only!
     * In other words: the using of setTransform is dangerous parctice.
     */
    @Override
    public void setTransform(AffineTransform Tx) {
        storage.debug(this.getNum() + " Draw setTransform DANGEROUS!");
        //The first step is to remove old transform
        //Get inverse of current transform
        AffineTransform old;
        try {
            old = transform.createInverse();
        } catch (NoninvertibleTransformException e) {
            throw new MyEPSException("Current transform is not invertable:" + e.getMessage());
        }
        double[] matr = new double[6];
        old.getMatrix(matr);
        //Apply inverse transform
        storage.append(this, "[" + matr[0] + " " + matr[1] + " " + matr[2] + " " + matr[3] + " " + matr[4] + //
                " " + matr[5] + "] cc");
        clip = transform.createTransformedShape(clip);
        //The second step is to set new transform
        if (Tx == null) {
            transform = new AffineTransform();
        } else {
            transform = new AffineTransform(Tx);
            try {
                clip = Tx.createInverse().createTransformedShape(clip);
            } catch (NoninvertibleTransformException e) {
                throw new MyEPSException("Setted transform is not invertable:" + e.getMessage());
            }
            Tx.getMatrix(matr);
            storage.append(this, "[" + matr[0] + " " + matr[1] + " " + matr[2] + " " + matr[3] + " " + matr[4] + //
                    " " + matr[5] + "] cc");
        }
    }

    @Override
    public AffineTransform getTransform() {
        storage.debug(this.getNum() + " Draw getTransform");
        return new AffineTransform(transform);
    }

    @Override
    public Paint getPaint() {
        storage.debug(this.getNum() + " Draw getPaint");
        return paint;
    }

    @Override
    public Composite getComposite() {
        storage.debug(this.getNum() + " Draw getComposite");
        return null;
    }

    @Override
    public void setBackground(Color color) {
        storage.debug(this.getNum() + " Draw setBackground");
        if (color == null) {
            storage.debug("   color ignored since it is null");
            return;
        }
        bgCol = color;
    }

    @Override
    public Color getBackground() {
        storage.debug(this.getNum() + " Draw getBackground");
        return bgCol;
    }

    @Override
    public Stroke getStroke() {
        storage.debug(this.getNum() + " Draw getStroke");
        return stroke;
    }

    @Override
    public void clip(Shape s) {
        storage.debug(this.getNum() + " Draw clip");
        Area area = new Area(clip);
        Area newA = new Area(s);
        newA.intersect(area);
        if (area.equals(newA))
            return;
        clip = newA;
        if (clip instanceof Rectangle2D) {
            Rectangle r = clip.getBounds();
            storage.append(this, r.x + " " + r.y + " " + r.width + " " + r.height + " rc");
        } else
            draw(clip, "cl");
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        storage.debug(this.getNum() + " Draw getFontRenderContext");
        return fontRenderContext;
    }


    //Graphics methods

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        storage.debug(this.getNum() + " Draw drawImage 3");
        return drawImage(img, x, y, Color.white, observer);
    }


    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        storage.debug(this.getNum() + " Draw drawImage 4");
        return drawImage(img, x, y, width, height, Color.white, observer);
    }


    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        storage.debug(this.getNum() + " Draw drawImage 5");
        if (img == null)
            return true;
        return drawImage(img, x, y, img.getWidth(null), img.getHeight(null), bgcolor, observer);
    }


    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        storage.debug(this.getNum() + " Draw drawImage 6");
        return drawImage(img, x, y, x + width, y + height, 0, 0, width, height, bgcolor, observer);
    }


    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
                             ImageObserver observer) {
        storage.debug(this.getNum() + " Draw drawImage 7");
        return drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, Color.white, observer);
    }

    /**
     * @return true if img is null and if specified rectangles have incorrect sizes:
     *   if (dx1 >= dx2)
     *   if (sx1 >= sx2)
     *   if (dy1 >= dy2)
     *   if (sy1 >= sy2)
     */
    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
                             Color bgcolor, ImageObserver observer) {
        //@param       img the specified image to be drawn. This method does
        //             nothing if <code>img</code> is null.
        storage.debug(this.getNum() + " Draw drawImage 8");
        if (img == null)
            return true;
        if (dx1 >= dx2)
            return true;
        if (sx1 >= sx2)
            return true;
        if (dy1 >= dy2)
            return true;
        if (sy1 >= sy2)
            return true;

        //Calculate the auxiliary variables
        int sWidth = sx2 - sx1;
        int sHeight = sy2 - sy1;
        int dWidth = dx2 - dx1;
        int dHeight = dy2 - dy1;

        //Get pixel information
        int[] pix = new int[sWidth * sHeight];
        PixelGrabber pg = new PixelGrabber(img, sx1, sy1, sx2 - sx1, sy2 - sy1, pix, 0, sWidth);
        try {
            pg.grabPixels();
        } catch (InterruptedException e) {
            return false;
        }

        //Create transformation for the image
        AffineTransform at = new AffineTransform();
        at.scale(sWidth / (double)dWidth, sHeight / (double)dHeight);
        at.translate(-dx1, -dy1);

        //Save current state of graphics write procedure
        storage.append(this, "gs");
        //store currant debuger state and swithch debugger of
        boolean inDebug = storage.isDebugged();
        storage.setDebug(false);
        double[] matr = new double[6];
        at.getMatrix(matr);
        storage.append(this,
                       sWidth + " " + sHeight + " 8 [" + matr[0] + " " + matr[1] + " " + matr[2] + " " + matr[3] +
                       " " + matr[4] + " " + matr[5] + "]");
        if (colorDepth == BLACK_AND_WHITE) {
            // Should really use imagemask.
            storage.append(this, "{currentfile " + sWidth + " string readhexstring pop} bind");
            storage.append(this, "image");
        } else if (colorDepth == GRAYSCALE) {
            storage.append(this, "{currentfile " + sWidth + " string readhexstring pop} bind");
            storage.append(this, "image");
        } else {
            storage.append(this, "{currentfile 3 " + sWidth + " mul string readhexstring pop} bind");
            storage.append(this, "false 3 colorimage");
        }
        //Write data 
        StringBuffer buf = new StringBuffer(90);
        for (int y = 0; y < sHeight; y++) {
            for (int x = 0; x < sWidth; x++) {
                Color color = new Color(pix[x + sWidth * y]);
                //Change the transparent color
                if (color.equals(bgcolor))
                    color = bgCol;

                if (colorDepth == BLACK_AND_WHITE) {
                    if ((color.getRed() + color.getGreen() + color.getBlue()) / 3d > 127) {
                        buf.append("ff");
                    } else {
                        buf.append("00");
                    }
                } else if (colorDepth == GRAYSCALE) {
                    buf.append(toHexString((color.getRed() + color.getGreen() + color.getBlue()) / 3));
                } else {
                    buf.append(toHexString(color.getRed()) + toHexString(color.getGreen()) +
                               toHexString(color.getBlue()));
                }

                if (buf.length() > 80) {
                    storage.append(this, buf.toString());
                    buf.setLength(0);
                }
            }
        }
        if (buf.length() > 0) {
            storage.append(this, buf.toString());
        }

        //Restore debugger state
        if (inDebug)
            storage.setDebug(inDebug);

        //Restore the graphics
        storage.append(this, "gr");

        return true;
    }


    @Override
    public Graphics create() {
        return new MyEPSGraphics(this);
    }

    @Override
    public Color getColor() {
        storage.debug(this.getNum() + " Draw getColor");
        return col;
    }

    @Override
    public void setColor(Color color) {
        storage.debug(this.getNum() + " Draw setColor final");
        if (color == null) {
            storage.debug("   color ignored since it is null");
            return;
        }
        if (col.equals(color))
            return;
        col = color;
        //Get float values of new colour
        float[] newCol = color.getRGBComponents(null);

        if (colorDepth == BLACK_AND_WHITE) {
            float value = 0;
            if (newCol[0] + newCol[1] + newCol[2] > 0.5) {
                value = 1;
            }
            storage.append(this, value + " sg");
        } else if (colorDepth == GRAYSCALE) {
            float value = (newCol[0] + newCol[1] + newCol[2]) / 3;
            storage.append(this, value + " sg");
        } else {
            //If alpha is less than 1 then proseed alpha composition of new colour with bgColor
            if (newCol[3] < 1) {
                //Get float values of old colour
                float[] oldCol = bgCol.getRGBComponents(null);
                for (int i = 0; i < 3; i++)
                    newCol[i] = newCol[i] * newCol[3] + oldCol[i] * (1 - newCol[3]);
            }
            storage.append(this, newCol[0] + " " + newCol[1] + " " + newCol[2] + " srgb");
        }
    }

    @Override
    public void setPaintMode() {
        storage.debug(this.getNum() + " Draw setPaintMode");
    }

    @Override
    public void setXORMode(Color c1) {
        storage.debug(this.getNum() + " Draw setXORMode");
    }

    @Override
    public Font getFont() {
        storage.debug(this.getNum() + " Draw getFont");
        return font;
    }

    @Override
    public void setFont(Font newFont) {
        storage.debug(this.getNum() + " Draw setFont");
        if (newFont == null) {
            newFont = Font.decode(null);
        }
        //Check for the differences between Fonts
        if (newFont.equals(font))
            return;
        font = newFont;
        EPSStorage.FontInf fi = storage.getFontInf(font);
        storage.append(this, "f" + fi.fontNum);
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        storage.debug(this.getNum() + " Draw getFontMetrics");
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        return g.getFontMetrics(f);
    }

    @Override
    public Rectangle getClipBounds() {
        storage.debug(this.getNum() + " Draw getClipBounds " + clip.getBounds());
        if (clip == null) {
            return null;
        }
        return clip.getBounds();
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        storage.debug(this.getNum() + " Draw clipRect");
        Area area = new Area(clip);
        Area newA = new Area(new Rectangle(x, y, width, height));
        newA.intersect(area);
        if (area.equals(newA))
            return;
        clip = newA;
        storage.append(this, x + " " + y + " " + width + " " + height + " rc");
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        storage.debug(this.getNum() + " Draw setClip");
        storage.append(this, "ic " + x + " " + y + " " + width + " " + height + " rc");
        storage.setCommand("ic");
        clip = new Rectangle(x, y, width, height);
    }

    @Override
    public void setClip(Shape clip) {
        if (clip == null)
            throw new MyEPSException("Try to set null clip!");
        storage.debug(this.getNum() + " Draw setClip");
        storage.append(this, "ic");
        //Check to rectangle
        if (clip instanceof Rectangle2D) {
            Rectangle r = clip.getBounds();
            storage.append(this, r.x + " " + r.y + " " + r.width + " " + r.height + " rc");
        } else
            draw(clip, "cl");
        this.clip = clip;
    }

    @Override
    public Shape getClip() {
        storage.debug(this.getNum() + " Draw getClip");
        return clip;
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        storage.debug(this.getNum() + " Draw copyArea");
        //It is not usible for EPS files.
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        storage.debug(this.getNum() + " Draw drawLine");
        storage.append(this, x1 + " " + y1 + " " + x2 + " " + y2 + " LI");
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        storage.debug(this.getNum() + " Draw fillRect");
        storage.append(this, x + " " + y + " " + (width+0.5) + " " + (height+0.5) + " rf");
    }

    @Override
    public void drawRect(int x, int y, int width, int height) {
        storage.debug(this.getNum() + " Draw drawRect");
        storage.append(this, x + " " + y + " " + width + " " + height + " rs");
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        storage.debug(this.getNum() + " Draw clearRect");
        Color old = getColor();
        setColor(bgCol);
        storage.append(this, x + " " + y + " " + width + " " + height + " rf");
        setColor(old);
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        storage.debug(this.getNum() + " Draw drawRoundRect");
        Shape shape = new RoundRectangle2D.Float(x, y, width, height, arcWidth, arcHeight);
        draw(shape, "st");
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        storage.debug(this.getNum() + " Draw fillRoundRect");
        Shape shape = new RoundRectangle2D.Float(x, y, width, height, arcWidth, arcHeight);
        draw(shape, "fl");
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        storage.debug(this.getNum() + " Draw drawOval");
        //Calculate the transformation of circle
        double trans = 1;
        trans = width / (double)height;
        double radX = width / 2d;
        double radY = height / 2d;
        storage.append(this, radY + " " + trans + " " + (x + radX) + " " + (y + radY) + " " + " do");
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        storage.debug(this.getNum() + " Draw fillOval");
        //Calculate the transformation of circle
        double trans = 1;
        trans = width / (double)height;
        double radX = width / 2d;
        double radY = height / 2d;
        storage.append(this, radY + " " + trans + " " + (x + radX) + " " + (y + radY) + " " + " fo");
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        storage.debug(this.getNum() + " Draw drawArc");
        StringBuffer buf = new StringBuffer(100);
        //Calculate the transformation of circle
        double trans = 1;
        trans = width / (double)height;
        double radX = width / 2d;
        double radY = height / 2d;
        buf.append(radY + " " + (-startAngle) + " " + (-startAngle - arcAngle) + " " + trans + " " + (x + radX) + " " +
                   (y + radY));
        if (arcAngle < 0)
            buf.append(" da");
        else
            buf.append(" dan");
        storage.append(this, buf.toString());
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        storage.debug(this.getNum() + " Draw fillArc");
        StringBuffer buf = new StringBuffer(100);
        //Calculate the transformation of circle
        double trans = 1;
        trans = width / (double)height;
        double radX = width / 2d;
        double radY = height / 2d;
        buf.append(radY + " " + (-startAngle) + " " + (-startAngle - arcAngle) + " " + trans + " " + (x + radX) + " " +
                   (y + radY));
        if (arcAngle < 0)
            buf.append(" fa");
        else
            buf.append(" fan");
        storage.append(this, buf.toString());
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        storage.debug(this.getNum() + " Draw drawPolyline");
        if (nPoints > 0) {
            storage.append(this, "np " + xPoints[0] + " " + yPoints[0] + " m");
            for (int i = 1; i < nPoints; i++) {
                storage.append(this, xPoints[i] + " " + yPoints[i] + " l");
            }
            storage.append(this, "st");
            storage.setCommand("np");
        }
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        storage.debug(this.getNum() + " Draw drawPolygon");
        if (nPoints > 0) {
            storage.append(this, "np " + xPoints[0] + " " + yPoints[0] + " m");
            for (int i = 1; i < nPoints; i++) {
                storage.append(this, xPoints[i] + " " + yPoints[i] + " l");
            }
            storage.append(this, "cp st");
            storage.setCommand("np");
        }
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        storage.debug(this.getNum() + " Draw fillPolygon");
        if (nPoints > 0) {
            storage.append(this, "np " + xPoints[0] + " " + yPoints[0] + " m");
            for (int i = 1; i < nPoints; i++) {
                storage.append(this, xPoints[i] + " " + yPoints[i] + " l");
            }
            storage.append(this, "cp fl");
            storage.setCommand("np");
        }
    }


    @Override
    public void dispose() {
        storage.debug(this.getNum() + " Dispose ");
        storage = null;
        col = null;
        bgCol = null;
        paint = null;
        stroke = null;
        font = null;
        clip = null;
        transform = null;
    }
}
