package de.hpi.swa.trufflesqueak.util;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

import javax.swing.JComponent;
import javax.swing.JFrame;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import de.hpi.swa.trufflesqueak.exceptions.SqueakException;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.FORM;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.model.WordsObject;

public final class SqueakDisplay {
    @CompilationFinal public static final int DEFAULT_WIDTH = 1024;
    @CompilationFinal public static final int DEFAULT_HEIGHT = 768;
    @CompilationFinal public static final Dimension DEFAULT_DIMENSION = new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    @CompilationFinal public static final int CURSOR_WIDTH = 16;
    @CompilationFinal public static final int CURSOR_HEIGHT = 16;

    public static AbstractSqueakDisplay create(boolean noDisplay) {
        if (!GraphicsEnvironment.isHeadless() && !noDisplay) {
            return new JavaDisplay();
        } else {
            return new NullDisplay();
        }
    }

    public static abstract class AbstractSqueakDisplay {
        public abstract void forceRect(int left, int right, int top, int bottom);

        public abstract Dimension getSize();

        public abstract void setFullscreen(boolean enable);

        public abstract void forceUpdate();

        public abstract void open();

        public abstract void close();

        public abstract void setSqDisplay(PointersObject sqDisplay);

        public abstract Point getLastMousePosition();

        public abstract int getLastMouseButton();

        public abstract int keyboardPeek();

        public abstract int keyboardNext();

        public abstract boolean isHeadless();

        public abstract void setCursor(int[] cursorWords);
    }

    private static class JavaDisplay extends AbstractSqueakDisplay {
        @CompilationFinal private final JFrame frame = new JFrame("TruffleSqueak");
        @CompilationFinal private final Canvas canvas = new Canvas();

        @CompilationFinal private final SqueakMouse mouse = new SqueakMouse();
        @CompilationFinal private final SqueakKeyboard keyboard = new SqueakKeyboard();

        @CompilationFinal private final static Toolkit toolkit = Toolkit.getDefaultToolkit();
        @CompilationFinal(dimensions = 1) private final static byte blackAndWhite[] = new byte[]{(byte) 0, (byte) 255};
        @CompilationFinal(dimensions = 1) private final static byte alphaComponent[] = new byte[]{(byte) 255};
        @CompilationFinal private final static ColorModel cursorModel = new IndexColorModel(1, 1, blackAndWhite, blackAndWhite, blackAndWhite, alphaComponent);

        public JavaDisplay() {
            canvas.addMouseListener(mouse);
            canvas.addMouseMotionListener(mouse);
            canvas.addKeyListener(keyboard);

            frame.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(canvas);
            frame.setResizable(true);
            frame.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent evt) {
                    canvas.resizeTo(frame.getSize());
                }
            });
        }

        class Canvas extends JComponent {
            private static final long serialVersionUID = 1L;
            @CompilationFinal private BufferedImage bufferedImage;
            @CompilationFinal private WordsObject bitmap;
            @CompilationFinal private int width;
            @CompilationFinal private int height;
            @CompilationFinal private int depth;

            private Canvas() {
                resizeTo(DEFAULT_DIMENSION);
            }

            @Override
            public void paintComponent(Graphics g) {
                if (bitmap == null) {
                    return;
                }
                //@formatter:off
                switch (depth) {
                    case 1: case 2: case 4: case 8: // colors need to be decoded
                        bufferedImage.setRGB(0, 0, width, height, decodeColors(), 0, width);
                        break;
                    case 16:
                        bufferedImage.setData(get16bitRaster());
                        break;
                    case 32: // use words directly
                        assert bitmap.getWords().length / width / height == 1;
                        bufferedImage.setRGB(0, 0, width, height, bitmap.getWords(), 0, width);
                        break;
                    default:
                        throw new SqueakException("Unsupported form depth: " + depth);
                }
                //@formatter:on
                g.drawImage(bufferedImage, 0, 0, null);
            }

            private int[] decodeColors() {
                int shift = 4 - depth;
                int pixelmask = ((1 << depth) - 1) << shift;
                int[] table = PIXEL_LOOKUP_TABLE[depth - 1];
                int[] words = bitmap.getWords();
                int[] rgb = new int[words.length];
                for (int i = 0; i < words.length; i++) {
                    int pixel = (words[i] & pixelmask) >> (shift - i * depth);
                    rgb[i] = table[pixel];
                    pixelmask >>= depth;
                }
                return rgb;
            }

            private Raster get16bitRaster() {
                int[] words = bitmap.getWords();
                assert words.length * 2 / width / height == 1;
                DirectColorModel colorModel = new DirectColorModel(16,
                                0x001f, // red
                                0x03e0, // green
                                0x7c00, // blue
                                0x8000  // alpha
                );
                WritableRaster raster = colorModel.createCompatibleWritableRaster(width, height);
                int word, high, low, x, y;
                Object pixel = null;
                for (int i = 0; i < words.length; i++) {
                    word = words[i];
                    high = word >> 16;
                    low = word & 0xffff;
                    x = (i % width / 2) * 2;
                    y = i / width;
                    pixel = colorModel.getDataElements(high, pixel);
                    raster.setDataElements(x, y, pixel);
                    pixel = colorModel.getDataElements(low, pixel);
                    raster.setDataElements(x + 1, y, pixel);
                }
                return raster;
            }

            private void resizeTo(Dimension newSize) {
                setSize(newSize);
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferedImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                repaint();
            }

            private void setSqDisplay(PointersObject sqDisplay) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.bitmap = (WordsObject) sqDisplay.at0(FORM.BITS);
                this.width = ((Long) sqDisplay.at0(FORM.WIDTH)).intValue();
                this.height = ((Long) sqDisplay.at0(FORM.HEIGHT)).intValue();
                this.depth = ((Long) sqDisplay.at0(FORM.DEPTH)).intValue();
            }
        }

        @Override
        @TruffleBoundary
        public void forceRect(int left, int right, int top, int bottom) {
            // TODO: repaint rect only instead of everything
            // canvas.repaint(left, top, right - left, bottom - top);
            canvas.repaint();
        }

        @Override
        @TruffleBoundary
        public void forceUpdate() {
            canvas.repaint();
        }

        @Override
        public void open() {
            if (!frame.isVisible()) {
                frame.setVisible(true);
                frame.requestFocus();
            }
        }

        @Override
        public void close() {
            frame.setVisible(false);
            frame.dispose();
        }

        @Override
        public Dimension getSize() {
            return frame.getSize();
        }

        @Override
        public void setFullscreen(boolean enable) {
            if (enable) {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setUndecorated(true);
            } else {
                frame.setExtendedState(JFrame.NORMAL);
                frame.setUndecorated(false);
            }
        }

        @Override
        public void setSqDisplay(PointersObject sqDisplay) {
            canvas.setSqDisplay(sqDisplay);
        }

        @Override
        public Point getLastMousePosition() {
            return mouse.getPosition();
        }

        @Override
        public int getLastMouseButton() {
            return mouse.getButtons() & 7 | keyboard.modifierKeys();
        }

        @Override
        public int keyboardPeek() {
            return keyboard.peekKey();
        }

        @Override
        public int keyboardNext() {
            return keyboard.nextKey();
        }

        @Override
        public boolean isHeadless() {
            return false;
        }

        @Override
        public void setCursor(int[] cursorWords) {
            Dimension bestCursorSize = toolkit.getBestCursorSize(CURSOR_WIDTH, CURSOR_HEIGHT);
            Cursor cursor;
            if (bestCursorSize.width == 0 || bestCursorSize.height == 0) {
                cursor = Cursor.getDefaultCursor();
            } else {
                DataBuffer buf = new DataBufferInt(cursorWords, (CURSOR_WIDTH * CURSOR_HEIGHT / 8) * 1);
                SampleModel sm = new MultiPixelPackedSampleModel(DataBuffer.TYPE_INT, CURSOR_WIDTH, CURSOR_HEIGHT, 1);
                WritableRaster raster = Raster.createWritableRaster(sm, buf, new Point(0, 0));
                Image cursorImage = new BufferedImage(cursorModel, raster, true, null);
                cursor = toolkit.createCustomCursor(cursorImage, new Point(0, 0), "TruffleSqueak Cursor");
            }
            canvas.setCursor(cursor);
        }
    }

    private static class NullDisplay extends AbstractSqueakDisplay {
        @Override
        public void forceRect(int left, int right, int top, int bottom) {
        }

        @Override
        public Dimension getSize() {
            return DEFAULT_DIMENSION;
        }

        @Override
        public void setFullscreen(boolean enable) {
        }

        @Override
        public void forceUpdate() {
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public void setSqDisplay(PointersObject sqDisplay) {
        }

        @Override
        public Point getLastMousePosition() {
            return new Point(0, 0);
        }

        @Override
        public int getLastMouseButton() {
            return 0;
        }

        @Override
        public int keyboardPeek() {
            return 0;
        }

        @Override
        public int keyboardNext() {
            return 0;
        }

        @Override
        public boolean isHeadless() {
            return true;
        }

        @Override
        public void setCursor(int[] cursorWords) {
        }
    }

    static final int[] PIXEL_LOOKUP_1BIT = {0xffffffff, 0xff000000};

    static final int[] PIXEL_LOOKUP_2BIT = {0xff000000, 0xff848484, 0xffc6c6c6, 0xffffffff};

    static final int[] PIXEL_LOOKUP_4BIT = {
                    0xff000000, 0xff000084, 0xff008400, 0xff008484,
                    0xff840000, 0xff840084, 0xff848400, 0xff848484,
                    0xffc6c6c6, 0xff0000ff, 0xff00ff00, 0xff00ffff,
                    0xffff0000, 0xffff00ff, 0xffffff00, 0xffffffff
    };

    static final int[] PIXEL_LOOKUP_8BIT = {
                    0xffffffff, 0xff000000, 0xffffffff, 0xff7f7f7f, 0xffff0000, 0xff00ff00,
                    0xff0000ff, 0xff00ffff, 0xffffff00, 0xffff00ff, 0xff1f1f1f, 0xff3f3f3f,
                    0xff5f5f5f, 0xff9f9f9f, 0xffbfbfbf, 0xffdfdfdf, 0xff070707, 0xff0f0f0f,
                    0xff171717, 0xff272727, 0xff2f2f2f, 0xff373737, 0xff474747, 0xff4f4f4f,
                    0xff575757, 0xff676767, 0xff6f6f6f, 0xff777777, 0xff878787, 0xff8f8f8f,
                    0xff979797, 0xffa7a7a7, 0xffafafaf, 0xffb7b7b7, 0xffc7c7c7, 0xffcfcfcf,
                    0xffd7d7d7, 0xffe7e7e7, 0xffefefef, 0xfff7f7f7, 0xff000000, 0xff003200,
                    0xff006500, 0xff009800, 0xff00cb00, 0xff00ff00, 0xff000032, 0xff003232,
                    0xff006532, 0xff009832, 0xff00cb32, 0xff00ff32, 0xff000065, 0xff003265,
                    0xff006565, 0xff009865, 0xff00cb65, 0xff00ff65, 0xff000098, 0xff003298,
                    0xff006598, 0xff009898, 0xff00cb98, 0xff00ff98, 0xff0000cb, 0xff0032cb,
                    0xff0065cb, 0xff0098cb, 0xff00cbcb, 0xff00ffcb, 0xff0000ff, 0xff0032ff,
                    0xff0065ff, 0xff0098ff, 0xff00cbff, 0xff00ffff, 0xff320000, 0xff323200,
                    0xff326500, 0xff329800, 0xff32cb00, 0xff32ff00, 0xff320032, 0xff323232,
                    0xff326532, 0xff329832, 0xff32cb32, 0xff32ff32, 0xff320065, 0xff323265,
                    0xff326565, 0xff329865, 0xff32cb65, 0xff32ff65, 0xff320098, 0xff323298,
                    0xff326598, 0xff329898, 0xff32cb98, 0xff32ff98, 0xff3200cb, 0xff3232cb,
                    0xff3265cb, 0xff3298cb, 0xff32cbcb, 0xff32ffcb, 0xff3200ff, 0xff3232ff,
                    0xff3265ff, 0xff3298ff, 0xff32cbff, 0xff32ffff, 0xff650000, 0xff653200,
                    0xff656500, 0xff659800, 0xff65cb00, 0xff65ff00, 0xff650032, 0xff653232,
                    0xff656532, 0xff659832, 0xff65cb32, 0xff65ff32, 0xff650065, 0xff653265,
                    0xff656565, 0xff659865, 0xff65cb65, 0xff65ff65, 0xff650098, 0xff653298,
                    0xff656598, 0xff659898, 0xff65cb98, 0xff65ff98, 0xff6500cb, 0xff6532cb,
                    0xff6565cb, 0xff6598cb, 0xff65cbcb, 0xff65ffcb, 0xff6500ff, 0xff6532ff,
                    0xff6565ff, 0xff6598ff, 0xff65cbff, 0xff65ffff, 0xff980000, 0xff983200,
                    0xff986500, 0xff989800, 0xff98cb00, 0xff98ff00, 0xff980032, 0xff983232,
                    0xff986532, 0xff989832, 0xff98cb32, 0xff98ff32, 0xff980065, 0xff983265,
                    0xff986565, 0xff989865, 0xff98cb65, 0xff98ff65, 0xff980098, 0xff983298,
                    0xff986598, 0xff989898, 0xff98cb98, 0xff98ff98, 0xff9800cb, 0xff9832cb,
                    0xff9865cb, 0xff9898cb, 0xff98cbcb, 0xff98ffcb, 0xff9800ff, 0xff9832ff,
                    0xff9865ff, 0xff9898ff, 0xff98cbff, 0xff98ffff, 0xffcb0000, 0xffcb3200,
                    0xffcb6500, 0xffcb9800, 0xffcbcb00, 0xffcbff00, 0xffcb0032, 0xffcb3232,
                    0xffcb6532, 0xffcb9832, 0xffcbcb32, 0xffcbff32, 0xffcb0065, 0xffcb3265,
                    0xffcb6565, 0xffcb9865, 0xffcbcb65, 0xffcbff65, 0xffcb0098, 0xffcb3298,
                    0xffcb6598, 0xffcb9898, 0xffcbcb98, 0xffcbff98, 0xffcb00cb, 0xffcb32cb,
                    0xffcb65cb, 0xffcb98cb, 0xffcbcbcb, 0xffcbffcb, 0xffcb00ff, 0xffcb32ff,
                    0xffcb65ff, 0xffcb98ff, 0xffcbcbff, 0xffcbffff, 0xffff0000, 0xffff3200,
                    0xffff6500, 0xffff9800, 0xffffcb00, 0xffffff00, 0xffff0032, 0xffff3232,
                    0xffff6532, 0xffff9832, 0xffffcb32, 0xffffff32, 0xffff0065, 0xffff3265,
                    0xffff6565, 0xffff9865, 0xffffcb65, 0xffffff65, 0xffff0098, 0xffff3298,
                    0xffff6598, 0xffff9898, 0xffffcb98, 0xffffff98, 0xffff00cb, 0xffff32cb,
                    0xffff65cb, 0xffff98cb, 0xffffcbcb, 0xffffffcb, 0xffff00ff, 0xffff32ff,
                    0xffff65ff, 0xffff98ff, 0xffffcbff, 0xffffffff
    };
    static final int[][] PIXEL_LOOKUP_TABLE = {
                    PIXEL_LOOKUP_1BIT,
                    PIXEL_LOOKUP_2BIT,
                    null,
                    PIXEL_LOOKUP_4BIT,
                    null,
                    null,
                    null,
                    PIXEL_LOOKUP_8BIT
    };
}
