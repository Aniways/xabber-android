/*
   Copyright 2013 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package fi2.harism.anndblur;

import fi2.harism.anndblur.ScriptC_StackBlur;
import fi2.harism.anndblur.ScriptField_RadiusStruct;
import fi2.harism.anndblur.ScriptField_SizeStruct;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.Script.LaunchOptions;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager.LayoutParams;

/**
 * Class for handling RenderScript based blur
 */
public class BlurRenderer {

    // Constant used for scaling the off-screen bitmap
	//TODO: should be 0.25
    private static final float BITMAP_SCALE_FACTOR = 1f;

    private final View mView;
    private final Canvas mCanvas;
    private final Matrix mMatrixScale;
    private final Matrix mMatrixScaleInv;
    // private final Rect mRectVisibleGlobal;
    private final int[] mLocationInWindow;
    private Bitmap mBitmap;

    private final RenderScript mRS;
    private final ScriptC_StackBlur mScriptStackBlur;
    private final LaunchOptions mLaunchOptions;
    private final ScriptField_RadiusStruct.Item mRadiusStruct;
    private final ScriptField_SizeStruct.Item mSizeStruct;

    private Allocation mAllocationBitmap;
    private Allocation mAllocationRgb;
    private Allocation mAllocationDv;

	private Bitmap mUnblured;

    /**
     * Default constructor
     */
    public BlurRenderer(View view) {
        mView = view;
        mCanvas = new Canvas();
        // mRectVisibleGlobal = new Rect();
        mLocationInWindow = new int[2];

        // Prepare matrices for scaling up/down the off-screen bitmap
        mMatrixScale = new Matrix();
        mMatrixScaleInv = new Matrix();

        // RenderScript related variables
        mRS = RenderScript.create(view.getContext());
        mScriptStackBlur = new ScriptC_StackBlur(mRS);
        mLaunchOptions = new LaunchOptions();
        mRadiusStruct = new ScriptField_RadiusStruct.Item();
        mSizeStruct = new ScriptField_SizeStruct.Item();
    }

    /**
     * Must be called from owning View.onAttachedToWindow
     */
    public void onAttachedToWindow() {
        // Start listening to onDraw calls
        mView.getViewTreeObserver().addOnPreDrawListener(onPreDrawListener);
    }

    /**
     * Must be called from owning View.onDetachedFromWindow
     */
    @SuppressLint("MissingSuperCall")
    public void onDetachedFromWindow() {
        // Remove listener
        mView.getViewTreeObserver().removeOnPreDrawListener(onPreDrawListener);
    }

    /**
     * Set blur radius in screen pixels. Value is mapped in range [1, 254].
     */
    public void setBlurRadius(int radius) {
        // Map radius into scaled down off-screen bitmap size
        radius = Math.round(radius * BITMAP_SCALE_FACTOR);

        // Setup radius struct
        mRadiusStruct.radius = Math.max(1, Math.min(254, radius));
        mRadiusStruct.div = mRadiusStruct.radius + mRadiusStruct.radius + 1;
        mRadiusStruct.divsum = (mRadiusStruct.div + 1) >> 1;
        mRadiusStruct.divsum *= mRadiusStruct.divsum;

        // Prepare dv allocation
        mAllocationDv = Allocation.createSized(mRS, Element.U8(mRS),
                256 * mRadiusStruct.divsum);
        mScriptStackBlur.set_initializeDv_divsum(mRadiusStruct.divsum);
        mScriptStackBlur.forEach_initializeDv(mAllocationDv);
    }

    /**
     * Returns true if this draw call originates from this class and is meant to
     * be an off-screen drawing pass.
     */
    public boolean isOffscreenCanvas(Canvas canvas) {
        return canvas == mCanvas;
    }

    /**
     * Applies blur to current off-screen bitmap
     */
    public void applyBlur() {
        
    	mAllocationBitmap.copyFrom(mBitmap);
        final Allocation output = Allocation.createTyped(mRS, mAllocationBitmap.getType());
        final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS));
        script.setRadius(12f /* e.g. 3.f */);
        script.setInput(mAllocationBitmap);
        script.forEach(output);
        output.copyTo(mBitmap);
    	
    	/*
    	// Copy current bitmap into allocation
        mAllocationBitmap.copyFrom(mBitmap);

        // Set script variables
        mScriptStackBlur.bind_dv(mAllocationDv);
        mScriptStackBlur.bind_bitmap(mAllocationBitmap);
        mScriptStackBlur.bind_rgb(mAllocationRgb);
        mScriptStackBlur.set_sizeStruct(mSizeStruct);
        mScriptStackBlur.set_radiusStruct(mRadiusStruct);

        // On first step iterate over y = [0, mBitmap.getHeight]
        mLaunchOptions.setX(0, 1);
        mLaunchOptions.setY(0, mBitmap.getHeight());
        mScriptStackBlur.forEach_blurHorizontal(mAllocationBitmap,
                mLaunchOptions);

        // On second step iterate over x = [0, mBitmap.getWidth]
        mLaunchOptions.setX(0, mBitmap.getWidth());
        mLaunchOptions.setY(0, 1);
        mScriptStackBlur
                .forEach_blurVertical(mAllocationBitmap, mLaunchOptions);

        // Copy bitmap allocation back to off-screen bitmap
        mAllocationBitmap.copyTo(mBitmap);
        */
    }

    /**
     * Draws off-screen bitmap into current canvas
     */
    public void drawToCanvas(Canvas canvas) {
        
    	
    	
    	if (mBitmap != null) {
    		
    		BitmapShader shader;
            shader = new BitmapShader(mBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setShader(shader);

            RectF rect = new RectF(0.0f, 0.0f, mBitmap.getWidth(), mBitmap.getHeight());

            canvas.drawBitmap(mUnblured, canvas.getMatrix(), new Paint());
            canvas.drawRoundRect(rect, 24, 24, paint);
            
            Paint mBorderPaint = new Paint();
            mBorderPaint.setStyle(Paint.Style.STROKE);
            mBorderPaint.setAntiAlias(true);
            mBorderPaint.setColor(Color.BLACK);
            mBorderPaint.setStrokeWidth(2);
            //canvas.drawRoundRect(rect, 24, 24, mBorderPaint);
    		
            // Draw off-screen bitmap using inverse of the scale matrix
            //canvas.drawBitmap(mBitmap, mMatrixScaleInv, null);
        }
    }

    /**
     * Private method for grabbing a "screenshot" of screen content
     */
    private void drawOffscreenBitmap() {
        // Grab global visible rect for later use
        // mView.getGlobalVisibleRect(mRectVisibleGlobal);

        // Calculate scaled off-screen bitmap width and height
        int width = Math.round(mView.getWidth() * BITMAP_SCALE_FACTOR);
        int height = Math.round(mView.getHeight() * BITMAP_SCALE_FACTOR);

        // This is added due to RenderScript limitations I faced.
        // If bitmap width is not multiple of 4 - in RenderScript
        // index = y * width
        // does not calculate correct index for line start index.
        width = width & ~0x03;

        // Width and height must be > 0
        width = Math.max(width, 1);
        height = Math.max(height, 1);

        // Allocate new off-screen bitmap only when needed
        //if (mBitmap == null || mBitmap.getWidth() != width
        //        || mBitmap.getHeight() != height) {
            mBitmap = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);
            
            mAllocationBitmap = Allocation.createFromBitmap(mRS, mBitmap);
            mAllocationRgb = Allocation.createSized(mRS, Element.U8_3(mRS),
                    width * height);
            mSizeStruct.width = width;
            mSizeStruct.height = height;
            // Due to adjusting width into multiple of 4 calculate scale matrix
            // only here
            mMatrixScale.setScale((float) width / mView.getWidth(),(float) height / mView.getHeight());
            mMatrixScale.invert(mMatrixScaleInv);
        //}

        // Translate values for off-screen drawing
        // int dx = -(Math.min(0, mView.getLeft()) + mRectVisibleGlobal.left);
        // int dy = -(Math.min(0, mView.getTop()) + mRectVisibleGlobal.top);
        //
        // Replaced dx and dy with View.getLocationInWindow() coordinates
        // because translate was bad for using BlurLinearLayout and
        // BlurRelativeLayout as children for other Views than root View.
        mView.getLocationInWindow(mLocationInWindow);
        // Restore canvas to its original state
        mCanvas.restoreToCount(1);
        mCanvas.setBitmap(mBitmap);
        // Using scale matrix will make draw call to match
        // resized off-screen bitmap size
        mCanvas.setMatrix(mMatrixScale);
        // Off-screen bitmap does not cover the whole screen
        // Use canvas translate to match its position on screen
        
        View rootViewPopup = mView.getRootView();
        LayoutParams params = (LayoutParams) rootViewPopup.getLayoutParams();
        
        int b4 = mCanvas.save();
        mUnblured =  Bitmap.createBitmap(width, height,Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas();
        c.setBitmap(mUnblured);
        
        mCanvas.translate(-params.x, -(params.y + 0));
        c.translate(-params.x, -(params.y + 0));
        mCanvas.save();
        c.save();
        
        
        // Start drawing from the root view
        View rootView = ((View)mView.getTag()).getRootView();
        
        rootView.draw(mCanvas);
        rootView.draw(c);
        mCanvas.restoreToCount(b4);
        
        mView.draw(mCanvas);
        
        Bitmap unblured = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        //Bitmap unblured = mBitmap.copy(Config.ARGB_8888, true);
        
        this.applyBlur();
        
        
        

        
        
      
        //mBitmap = unblured;
         
    }

    /**
     * Listener for receiving onPreDraw calls from underlying ui
     */
    private final ViewTreeObserver.OnPreDrawListener onPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            // Only care if View we are doing work for is visible
            if (mView.getVisibility() == View.VISIBLE) {
                drawOffscreenBitmap();
            }
            return true;
        }
    };
    
    @SuppressLint("NewApi") private final ViewTreeObserver.OnDrawListener onDrawListener = new ViewTreeObserver.OnDrawListener() {
        @Override
        public void onDraw() {
            // Only care if View we are doing work for is visible
            if (mView.getVisibility() == View.VISIBLE) {
                //drawOffscreenBitmap();
            }
            return;
        }
    };

}
