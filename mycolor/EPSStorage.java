package mycolor;


import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;

import java.io.BufferedWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Stack;


/**
 * This class is the storage of all EPS file information
 * This class also control the using of MyEPSGraphics and
 * dispose it when it is necessary
 *
 * The one of the most important part of this class work
 * is the control of font usage. When font is setted in
 * MyEPSGraphics it is checked for previous using and is
 * added to font dictionary.
 * @author Evgeny Mirkes (University of Leicester, UK)
 */
public class EPSStorage {

    private static final int fontFrac = 3;
    private BufferedWriter bw = null;
    private ArrayList<String> body = new ArrayList<String>();

    private static HashMap<String, Commands> commands = null;
    private static int opsNumber = 2;
    private boolean[] needed = new boolean[opsNumber];
    static {
        commands = new HashMap<String, Commands>();
        int k = 0;
        commands.put("bd", new Commands("/bd{bind def}bind def", new String[] { }, k++));
        commands.put("gs", new Commands("/gs{gsave}bd", new String[] { }, k++));
        commands.put("gr", new Commands("/gr{grestore}bd", new String[] { }, k++));
        commands.put("tr", new Commands("/tr{translate}bd", new String[] { }, k++));
        commands.put("sc", new Commands("/sc{scale}bd", new String[] { }, k++));
        commands.put("np", new Commands("/np{newpath}bd", new String[] { }, k++));
        commands.put("cp", new Commands("/cp{closepath}bd", new String[] { }, k++));
        commands.put("cl", new Commands("/cl{clip}bd", new String[] { }, k++));
        commands.put("st", new Commands("/st{stroke}bd", new String[] { }, k++));
        commands.put("fl", new Commands("/fl{fill}bd", new String[] { }, k++));
        commands.put("c", new Commands("/c{curveto}bd", new String[] { }, k++));
        commands.put("l", new Commands("/l{lineto}bd", new String[] { }, k++));
        commands.put("m", new Commands("/m{moveto}bd", new String[] { }, k++));
        commands.put("ss", new Commands("/ss{setlinewidth setmiterlimit setlinejoin setlinecap setdash}bd", //
                    new String[] { }, k++));
        commands.put("r", new Commands("/r{rotate}bd", new String[] { }, k++));
        commands.put("cc", new Commands("/cc{concat}bd", new String[] { }, k++));
        commands.put("sg", new Commands("/sg{setgray}bd", new String[] { }, k++));
        commands.put("srgb", new Commands("/srgb{setrgbcolor}bd", new String[] { }, k++));
        commands.put("f", new Commands("/f{findfont exch scalefont setfont}bd", new String[] { }, k++));
        commands.put("ic", new Commands("/ic{initclip}bd", new String[] { }, k++));
        commands.put("LI", new Commands("/LI{np m l st}bd", new String[] { "np", "m", "l", "st" }, k++));
        commands.put("rf", new Commands("/rf{rectfill}bd", new String[] { }, k++));
        commands.put("rs", new Commands("/rs{rectstroke}bd", new String[] { }, k++));
        commands.put("rc", new Commands("/rc{rectclip}bd", new String[] { }, k++));
        commands.put("s", new Commands("/s{m gs 1 -1 sc show gr}bd", new String[] { "m", "gs", "sc", "gr" }, k++));
        commands.put("ed", new Commands("/ed{exch def}bd", new String[] { }, k++));
        commands.put("do", new Commands("/do{gs np tr 1/S ed S sc/R ed 0 0 R 0 360 arc st gr}bd", //
                    new String[] { "gs", "np", "st", "gr", "sc", "ed", "tr" }, k++));
        commands.put("fo", new Commands("/fo{gs np tr 1/S ed S sc/R ed 0 0 R 0 360 arc fl gr}bd", //
                    new String[] { "gs", "np", "fl", "gr", "sc", "ed", "tr" }, k++));
        commands.put("da", new Commands("/da{gs np tr  1/S ed S sc/A1 ed/A2 ed/R ed 0 0 R A2 A1 st gr}bd", //
                    new String[] { "gs", "np", "st", "gr", "tr", "sc", "ed" }, k++));
        commands.put("dan", new Commands("/dan{gs np tr  1/S ed S sc/A1 ed/A2 ed/R ed 0 0 R A2 A1 arcn st gr}bd", //
                    new String[] { "gs", "np", "st", "gr", "tr", "sc", "ed" }, k++));
        commands.put("fa", new Commands("/fa{gs np tr 0 0 m 1/S ed S sc/A ed/D ed/R ed 0 0 R D A arc fl gr}bd", //
                    new String[] { "gs", "np", "fl", "gr", "ed", "m" }, k++));
        commands.put("fan", new Commands("/fa{gs np tr 0 0 m 1/S ed S sc/A ed/D ed/R ed 0 0 R D A arcn fl gr}bd", //
                    new String[] { "gs", "np", "fl", "gr", "ed", "m" }, k++));
        opsNumber = commands.size();
    }

    private Stack<MyEPSGraphics> graphStack = new Stack<MyEPSGraphics>();
    private HashMap<Font, FontInf> fontMap = new HashMap<Font, FontInf>();
    private int nextFont = 0;
    private boolean inDebug = false;
    private boolean encapsulateFonts = true;
    private static final String specSymb = "()[]{}<>/% ";

    /**
     * Create the new storage and initiate data.
     * @param width is width of window
     * @param height is height of window
     * @param title is the value for title comment in eps file
     * @param copyright is the value for copyright comment in eps file
     * @param bw is the BufferedWriter which will be used to write eps file
     * @throws IOException - if an I/O error occures
     */
    public EPSStorage(float width, float height, String title, String copyright,
                      BufferedWriter bw) throws IOException {
        this.bw = bw;

        write("%!PS-Adobe-3.0 EPSF-3.0");
        write("%%Creator: MyEPSGraphics by Evgeny Mirkes (University of Leicester)");
        write("%%Title: " + title);
        write("%%Copyright: " + copyright);
        write("%%CreationDate: " + new Date());
        write("%%BoundingBox: 0 0 " + ((int)Math.ceil(width)) + " " + ((int)Math.ceil(height)));
        write("%%Origin: 0 0");
        write("%%Pages: 1");
        write("%%Page: 1 1");
        write("%%EndComments");
        write("%%BeginProlog");

        body.add("% Creation storage");
        //Transform the coordinate system to coincide with Graphics
        body.add("0 " + height + " tr");
        body.add("1 -1 sc");
        setCommand("tr");
        setCommand("sc");
        setCommand("gs");
        setCommand("gr");
        needed[0] = true;
    }

    /**
     * Debug procedure. Must be eliminated at the end
     * @param s is the string to add to file as comment
     */
    public void debug(String s) {
        if (inDebug)
            body.add("% " + s);
    }

    /**
     * Set the indicator of command using.
     * @param com is the tested command
     */
    protected void setCommand(String com) {
        Commands c = commands.get(com);
        if (c == null)
            return;
        if (needed[c.num])
            return;
        needed[c.num] = true;
        for (String s : c.linked)
            setCommand(s);
    }

    /**
     * Appends a line to the EPAStorage.  A new line character is added
     * to the end of the line when it is added.
     * @param g is MyEPSGraphics which call method. This parameter is used to define the state of
     *          graphics stack
     * @param line is the data to add to the eps file
     */
    public synchronized void append(MyEPSGraphics g, String line) {
        debug("append from " + g.getNum());
        //Define is it new graphics
        if (graphStack.isEmpty()) {
            //It is start of work! Push first element and save graph
            graphStack.push(g);
            body.add("gs");
        } else {
            //We have some elements.
            //Current is the last who appends data?
            MyEPSGraphics old = graphStack.peek();
            if (!old.equals(g)) {
                //graphics changed. Search the current in stack:
                int n = graphStack.search(g);
                if (n == -1) {
                    //there is no current in stack. Search current's immediaate parent
                    n = graphStack.search(g.getParent());
                    if (n == -1) {
                        //WE have the troubles! Print stack and finish work!
                        body.add("Current number:\t" + g.getNum());
                        //Firstly print all parents
                        old = g.getParent();
                        while (old != null) {
                            body.add("\tparent:\t" + old.getNum());
                            old = old.getParent();
                        }
                        //Secondly print the stack
                        while (!graphStack.isEmpty()) {
                            old = graphStack.pop();
                            body.add("\tstack:\t" + old.getNum());
                        }
                        try {
                            close(g);
                        } catch (IOException e) {
                            ;
                        }
                        throw new MyEPSException("Graph stack disaster");
                    }
                }
                //now we know the number of elements in steck to pop
                for (int i = 1; i < n; i++) {
                    graphStack.pop();
                    body.add("gr");
                }
                if (!graphStack.peek().equals(g)) {
                    //Now we have to add current element to stack
                    graphStack.push(g);
                    body.add("gs");
                }
            }
        }
        if (line == null || line.isEmpty())
            return;
        body.add(line);
        //Find last command and test it
        int n = line.lastIndexOf(" ");
        if (n > -1)
            line = line.substring(n + 1);
        setCommand(line);
    }

    /**
     * @param ef is true for encapsulated fonts and fale otherwise
     */
    public void encapsulateFonts(boolean ef) {
        encapsulateFonts = ef;
    }

    private void write(String s) throws IOException {
        bw.write(s);
        bw.newLine();
    }

    /**
     * @param who is the MyEPSGraphics which call close method.
     * @throws IOException - if an I/O error occures
     */
    public void close(MyEPSGraphics who) throws IOException {
        debug("Close storage ");
        //Prepare the fonts description
        Font[] keys = fontMap.keySet().toArray(new Font[fontMap.size()]);
        //Check fonts to add commands for fonts
        for (Font f : keys) {
            //Check for font using
            FontInf fi = fontMap.get(f);
            if (fi.used.length() > 0) {
                setCommand("f");
                break;
            }
        }

        ArrayList<String> fontS = new ArrayList<String>();
        FontRenderContext frc = who.getFontRenderContext();
        if (encapsulateFonts) {
            TextLayout layout;
            Shape shap;
            AffineTransform at = new AffineTransform();
            int type = 0;
            float[] coords = new float[6];
            PathIterator it;
            float x0, y0;
            ArrayList<String> fNames = new ArrayList<String>();
            ArrayList<Font> fonts = new ArrayList<Font>();
            ArrayList<StringBuffer> fChar = new ArrayList<StringBuffer>();
            //search the unique font names
            for (Font f : keys) {
                //Check for font using
                FontInf fi = fontMap.get(f);
                if (fi.used.length() > 0) {
                    Font ff = new Font(f.getName(), f.getStyle(), 50);
                    String s = ff.getPSName();
                    s = s.replaceAll("\\.", "_");
                    int n = fNames.indexOf(s);
                    if (n == -1) {
                        //Create new record if new font
                        fNames.add(s);
                        fChar.add(fi.used);
                        fonts.add(ff);
                    } else {
                        //Copy used characters to existing copy of this font.
                        StringBuffer sb = fChar.get(n);
                        for (int i = 0; i < fi.used.length(); i++) {
                            char ch = fi.used.charAt(i);
                            if (sb.indexOf("" + ch) == -1)
                                sb.append(ch);
                        }

                    }
                }
            }
            //get one font and prepare it's data
            for (int i = 0; i < fNames.size(); i++) {
                String nam = fNames.get(i);
                StringBuffer sb = fChar.get(i);
                Font f = fonts.get(i);
                //First step - define maximum char code
                int max = 0;
                int chars = sb.length();
                //Calculate the number of maximum symbol
                //If exist space then move it to last position
                for (int j = 0; j < chars; j++) {
                    char ch = sb.charAt(j);
                    if (ch==' ' && j<chars-1){
                        ch = sb.charAt(chars-1);
                        sb.setCharAt(j, ch);
                        sb.setCharAt(chars-1, ' ');
                    }
                    int k = ch & 0x7f;
                    if (k > max)
                        max = k;
                }
                //write heard to body or to appropriate buffers
                if (fontS.size() == 0)
                    fontS.add("/Ec{Encoding}bd");
                fontS.add("%% ------  /" + nam + "  -------");
                fontS.add("10 dict dup begin"); //Create dictionary
                fontS.add("/FontType 3 def"); //the first element
                fontS.add("/FontMatrix [0.02 0 0 -0.02 0 0] def"); //the second element
                fontS.add("/FontBBox [-35 -35 134 155] def"); //the third element 
                //the fourth element array fill array to undefined elements
                fontS.add("/Encoding " + (max + 1) + " array def 0 1 " + max + "{Ec exch /.notdef put}for");
                //Set names of elements
                StringBuffer buf = new StringBuffer(100);
                for (int j = 0; j < chars; j++) {
                    char ch = sb.charAt(j);
                    int k = ch & 0x7f;
                    //There are some special symbols in postscript: () {} [] <> / %
                    //We must change this symbols for the using as fontname
                    int n = specSymb.indexOf(ch);
                    String s;
                    if (n > -1)
                        s = "D" + n;
                    else
                        s = "" + ch;
                    buf.append(" Ec " + k + " /D" + s + " put");
                    if (buf.length() > 80) {
                        fontS.add(buf.toString());
                        buf.setLength(0);
                    }
                }
                if (buf.length() > 0) { //End of array filling
                    fontS.add(buf.toString());
                    buf.setLength(0);
                }
                //Calculate and store all rectangles
                ArrayList<Rectangle> metrix = new ArrayList<Rectangle>(max);
                for (int j = 0; j < chars; j++) {
                    char ch = sb.charAt(j);
                    layout = new TextLayout("" + ch, f, frc);
                    Rectangle r = layout.getPixelBounds(frc, 0, 0);
                    r.x--;
                    r.y--;
                    r.width += 2;
                    r.height += 2;
                    metrix.add(r);
                }
                layout = new TextLayout(" ", f, frc);
                // the fifth element – dictionary of symbols width fill
                //Calculate average width of symbols
                int wid=0,ww;
                buf.append("/Metrics " + (chars + 1) + " dict def Metrics begin /.notdef 10 def");
                for (int j = 0; j < chars; j++) {
                    char ch = sb.charAt(j);
                    Rectangle r = metrix.get(j);
                    int n = specSymb.indexOf(ch);
                    String s;
                    if (n > -1)
                        s = "D" + n;
                    else
                        s = "" + ch;
                    if (ch!=' '){
                        ww=r.x+r.width;
                        wid+=ww;
                    } else {
                        ww=wid/(2*(chars-1));
                    }
                    buf.append(" " + " /D" + s + " " + ww + " def");
                    if (buf.length() > 80) {
                        fontS.add(buf.toString());
                        buf.setLength(0);
                    }
                }
                buf.append(" end"); //the fifth element – dictionary of symbols width fill
                fontS.add(buf.toString());
                buf.setLength(0);
                //the sixth element – dictionary of symbols boxes fill
                buf.append("/BBox " + (chars + 1) + " dict def BBox begin /.notdef [0 0 0 0] def");
                for (int j = 0; j < chars; j++) {
                    char ch = sb.charAt(j);
                    Rectangle r = metrix.get(j);
                    int n = specSymb.indexOf(ch);
                    String s;
                    if (n > -1)
                        s = "D" + n;
                    else
                        s = "" + ch;
                    buf.append(" /D" + s + " [" + r.x + " " + r.y + " " + (r.x + r.width) + " " + (r.y + r.height) +
                               "] def");
                    if (buf.length() > 80) {
                        fontS.add(buf.toString());
                        buf.setLength(0);
                    }
                }
                buf.append(" end"); //the sixth element – dictionary of symbols boxes fill
                fontS.add(buf.toString());
                buf.setLength(0);
                //the seventh element – dictionary of Char procedures start of definition
                buf.append("/CharProcs " + (chars + 1) + " dict def CharProcs begin /.notdef {} def");

                for (int j = 0; j < chars; j++) {
                    char ch = sb.charAt(j);
                    layout = new TextLayout("" + ch, f, frc);
                    shap = layout.getOutline(at);
                    //Each character description have to start from new line
                    if (buf.length() > 0) {
                        fontS.add(buf.toString());
                        buf.setLength(0);
                    }
                    // Add start of definition
                    int n = specSymb.indexOf(ch);
                    String s;
                    if (n > -1)
                        s = "D" + n;
                    else
                        s = "" + ch;
                    buf.append(" " + " /D" + s + "{np");
                    //Transform the shape to pan movement and store in buffer
                    type = 0;
                    it = shap.getPathIterator(null);
                    x0 = 0;
                    y0 = 0;
                    while (!it.isDone()) {
                        type = it.currentSegment(coords);

                        if (type == PathIterator.SEG_CLOSE) {
                            // Close path by PS command
                            buf.append(" cp");
                        } else if (type == PathIterator.SEG_CUBICTO) {
                            //Cubic line
                            buf.append(" " + floatFormat(coords[0]) + " " + floatFormat(coords[1]) + " " +
                                       floatFormat(coords[2]) + " " + floatFormat(coords[3]) + " " +
                                       floatFormat(coords[4]) + " " + floatFormat(coords[5]) + " c");
                            x0 = coords[4];
                            y0 = coords[5];
                        } else if (type == PathIterator.SEG_LINETO) {
                            //Linear segment
                            buf.append(" " + floatFormat(coords[0]) + " " + floatFormat(coords[1]) + " l");
                            x0 = coords[0];
                            y0 = coords[1];
                        } else if (type == PathIterator.SEG_MOVETO) {
                            //Linear segment without line
                            buf.append(" " + floatFormat(coords[0]) + " " + floatFormat(coords[1]) + " m");
                            x0 = coords[0];
                            y0 = coords[1];
                        } else if (type == PathIterator.SEG_QUADTO) {
                            //Quadric segment. There is no quadric comand in PS
                            // Transform one calibrate point to two calibrate points for the cubic
                            // Bernstein polinom which draw exactly the same corve
                            float q1x = (x0 + 2 * coords[0]) / 3;
                            float q1y = (y0 + 2 * coords[1]) / 3;
                            float q2x = (2 * coords[0] + coords[2]) / 3;
                            float q2y = (2 * coords[1] + coords[3]) / 3;
                            buf.append(" " + floatFormat(q1x) + " " + floatFormat(q1y) + " " + floatFormat(q2x) + " " +
                                       floatFormat(q2y) + " " + floatFormat(coords[2]) + " " + floatFormat(coords[3]) +
                                       " c");
                            x0 = coords[2];
                            y0 = coords[3];
                        }
                        it.next();
                        if (buf.length() > 80) {
                            fontS.add(buf.toString());
                            buf.setLength(0);
                        }
                    }
                    buf.append(" fl}bd");
                }
                buf.append(" end"); //the seventh element – dictionary of Char procedures end of definition
                fontS.add(buf.toString());
                buf.setLength(0);
                // The eighth element - standard element
                fontS.add("/BuildChar{0 begin /char ed /fontdict ed /charn fontdict /Encoding get");
                fontS.add("char get def fontdict begin Metrics charn get 0 BBox charn get aload pop");
                fontS.add("setcachedevice CharProcs charn get exec end end} def");
                // The ninth and tenth elements - standard elements and the end of main dictionary
                fontS.add("/BuildChar load 0 3 dict put /UniqueID 1 def end");
                fontS.add("/" + nam + " exch definefont pop");
                fontS.add("%% --- end of font --------");
            }
            //Specify additional commands:
            setCommand("c");
            setCommand("l");
            setCommand("m");
            setCommand("ed");
            setCommand("cp");
            setCommand("fl");
        }
        //Write short srtings
        //Get full list of commands
        Commands[] coms = commands.values().toArray(new Commands[commands.size()]);
        Arrays.sort(coms, new Comparator<Commands>() {
                @Override
                public int compare(Commands o1, Commands o2) {
                    int n = o1.num - o2.num;
                    return n;
                }
            });

        for (int i = 0; i < opsNumber; i++) {
            //Write used commands only
            if (needed[i]) {
                write(coms[i].spelling);
            }
        }

        for (String s : fontS) {
            write(s);
        }

        //Prepare font descriptors
        for (Font f : keys) {
            //Write only used fonts!
            FontInf fi = fontMap.get(f);
            if (fi.used.length() > 0) {
                Font ff = new Font(f.getName(), f.getStyle(), 1);
                write("/f" + fi.fontNum + "{" + ((int)f.getSize()) + " /" + ff.getPSName().replaceAll("\\.", "_") +
                      " f}bd");
            } else {
                String s = "f" + fi.fontNum;
                for (int i = body.size() - 1; i > 0; i--)
                    if (s.equals(body.get(i)))
                        body.remove(i);
            }
        }

        write("%%EndProlog");

        //clear stack
        while (!graphStack.isEmpty()) {
            graphStack.pop();
            body.add("gr");
        }

        //Clear data. This fragment must be commented for the debugging
        if (!inDebug) {
            //Convert data to other format
            ComList first = new ComList("", null);
            ComList cl = first;
            for (String s : body)
                cl = cl.add(s);
            //Te following combinations investigated
            // 1. gs gr without any operations between can be removed. In this case ComList has one child only. Done
            // 2. gs gs some operations  gr gr one gs and one gr can be deleted. In this case tested ComList has
            //    exectly two child and child with number zero has name "gr". Copy childs name and childs to
            //    corresponding fields of tested ComList.
            first.dummy();

            //Back convert result
            body.clear();
            first.store(body);
        }
        //Write data
        for (String s : body) {
            write(s);
        }


        write("showpage");
        write("%%EOF");
        bw.close();
    }

    private String floatFormat(float x) {
        return String.format(Locale.US, "%" + 1 + "." + fontFrac + "f", x);
    }

    /**
     * Set the inDebug field to specified value.
     * @param debug is value to set
     */
    protected void setDebug(boolean debug) {
        inDebug = debug;
    }

    /**
     * @return the current value of inDebug
     */
    protected boolean isDebugged() {
        return inDebug;
    }

    /**
     * @param f is the font to get information
     * @return the font information for asked font
     */
    public FontInf getFontInf(Font f) {
        FontInf fi = fontMap.get(f);
        if (fi == null) {
            fi = new FontInf(nextFont++);
            fontMap.put(f, fi);
        }
        return fi;
    }

    protected static class FontInf {
        protected int fontNum;
        protected StringBuffer used = new StringBuffer(128);

        /**
         * @param n is the number of new font information object
         */
        protected FontInf(int n) {
            fontNum = n;
        }

        /**
         * @param ch is the character to add to the buffer of used symbols of current font
         */
        protected void addSymbols(char ch) {
            if (used.indexOf("" + ch) == -1)
                used.append(ch);
        }

    }

    protected static class ComList {
        protected String name;
        protected ArrayList<ComList> child;
        protected ComList parent;

        private ComList(String s, ComList par) {
            name = s;
            child = new ArrayList<ComList>();
            parent = par;
        }

        /**
         * @param s is the string to add to the command list structure.
         *         All strings which is started with "% " substring will be ignored.
         *         Each string "gs" create new sub list as child of current list
         *         Each string "gr" closed the current sublist end return the
         *         link to parent list
         * @return the current active list
         */
        protected ComList add(String s) {
            //Ignore comments
            if (s.startsWith("% "))
                return this;
            ComList cl = new ComList(s, this);
            child.add(cl);
            //If it is gs then start new level
            if ("gs".equals(s))
                return cl;
            //If it is gr then return on level up
            if ("gr".equals(s))
                return parent;
            return this;
        }

        /**
         * @param body is the array to store the list structure
         * @param s is the string with some numbers of spaces to ident current list level
         */
        protected void store(ArrayList<String> body, String s) {
            body.add(s + name);
            for (ComList cl : child)
                cl.store(body, s + "  ");
        }

        /**
         * @param body is the array to store the list structure
         * This version of method store data without identing the list levels.
         */
        protected void store(ArrayList<String> body) {
            body.add(name);
            for (ComList cl : child)
                cl.store(body);
        }

        protected void dummy() {
            for (int i = child.size() - 1; i >= 0; i--) {
                ComList cl = child.get(i);
                cl.dummy();
                if (cl.child.size() == 1) {
                    child.remove(i);
                }
            }
            if (!name.isEmpty() && child.size() == 2 && "gs".equals(child.get(0).name)) {
                ComList cl = child.get(0);
                this.name = cl.name;
                this.child = cl.child;
                cl.parent = this.parent;
            }
        }
    }

    protected static class Commands {
        private String spelling;
        private String[] linked;
        private int num;

        private Commands(String spell, String[] links, int n) {
            spelling = spell;
            linked = links;
            num = n;
        }
    }
}
