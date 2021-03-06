package im.zego.zegoexpress.faceu.faceunity.view;

import static im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel.STR_FILTER_LEVEL;
import static im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel.getValue;
import static im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel.isOpen;
import static im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel.sFilter;
import static im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel.sFilterLevel;
import static im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel.sHeavyBlur;
import static im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel.sSkinDetect;
import static im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel.setValue;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.List;

import im.zego.zegoexpress.R;
import im.zego.zegoexpress.faceu.faceunity.OnFUControlListener;
import im.zego.zegoexpress.faceu.faceunity.entity.Effect;
import im.zego.zegoexpress.faceu.faceunity.entity.FaceMakeup;
import im.zego.zegoexpress.faceu.faceunity.entity.Filter;
import im.zego.zegoexpress.faceu.faceunity.entity.MakeupItem;
import im.zego.zegoexpress.faceu.faceunity.view.adapter.BaseRecyclerAdapter;
import im.zego.zegoexpress.faceu.faceunity.view.adapter.VHSpaceItemDecoration;
import im.zego.zegoexpress.faceu.faceunity.view.entity.BeautyParameterModel;
import im.zego.zegoexpress.faceu.faceunity.view.entity.EffectEnum;
import im.zego.zegoexpress.faceu.faceunity.view.entity.FaceMakeupEnum;
import im.zego.zegoexpress.faceu.faceunity.view.entity.FilterEnum;
import im.zego.zegoexpress.faceu.faceunity.view.seekbar.DiscreteSeekBar;
import im.zego.zegoexpress.faceu.faceunity.view.utils.OnMultiClickListener;
import im.zego.zegoexpress.faceu.faceunity.FURenderer;



/**
 * Created by tujh on 2017/8/15.
 */
public class BeautyControlView extends FrameLayout {
    private static final String TAG = "BeautyControlView";
    private Context mContext;

    private OnFUControlListener mOnFUControlListener;
    private RecyclerView mRvMakeupItems;

    public void setOnFUControlListener(@NonNull FURenderer onFUControlListener) {
        mOnFUControlListener = onFUControlListener;
    }

    private CheckGroup mBottomCheckGroup;

    private HorizontalScrollView mSkinBeautySelect;
    private BeautyBoxGroup mSkinBeautyBoxGroup;
    private BeautyBox mBoxSkinDetect;
    private BeautyBox mBoxHeavyBlur;
    private BeautyBox mBoxBlurLevel;
    private BeautyBox mBoxEyeBright;
    private BeautyBox mBoxToothWhiten;
    private BeautyBoxGroup mFaceShapeBeautyBoxGroup;
    private FrameLayout mFlFaceShapeItems;
    private ImageView mIvRecoverFaceShpe;
    private TextView mTvRecoverFaceShpe;

    private RecyclerView mFilterRecyclerView;
    private FilterRecyclerAdapter mFilterRecyclerAdapter;
    private List<Filter> mFilters;

    private DiscreteSeekBar mBeautySeekBar;
    private FaceMakeupAdapter mFaceMakeupAdapter;
    private RelativeLayout mFaceShapeLayout;
    private boolean isShown;
    private boolean mFirstShowLightMakeup = true;

    private RecyclerView mEffectRecyclerView;
    private EffectRecyclerAdapter mEffectRecyclerAdapter;
    private int mEffectType = Effect.EFFECT_TYPE_NORMAL;

    public BeautyControlView(Context context) {
        this(context, null);
    }

    public BeautyControlView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    // ??????????????????
    private int mFilterPositionSelect = 0;

    public BeautyControlView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        mFilters = FilterEnum.getFiltersByFilterType();
        LayoutInflater.from(context).inflate(R.layout.layout_beauty_control, this);
        initView();
    }

    private void initView() {
        initViewBottomRadio();
        initViewSkinBeauty();
        initViewFaceShape();
        initViewFilterRecycler();
        initMakeupView();
        initViewEffectRecycler();
        initViewTop();
    }

    public void onResume() {
        updateViewSkinBeauty();
        updateViewFaceShape();
        updateViewFilterRecycler();
        hideBottomLayoutAnimator();
    }

    private void initMakeupView() {
        mRvMakeupItems = findViewById(R.id.rv_face_makeup);
        mRvMakeupItems.setHasFixedSize(true);
        ((SimpleItemAnimator) mRvMakeupItems.getItemAnimator()).setSupportsChangeAnimations(false);
        mRvMakeupItems.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false));
        mRvMakeupItems.addItemDecoration(new VHSpaceItemDecoration(0, getResources().getDimensionPixelSize(R.dimen.x15)));
        mFaceMakeupAdapter = new FaceMakeupAdapter(FaceMakeupEnum.getBeautyFaceMakeup());
        OnFaceMakeupClickListener onMpItemClickListener = new OnFaceMakeupClickListener();
        mFaceMakeupAdapter.setOnItemClickListener(onMpItemClickListener);
        mRvMakeupItems.setAdapter(mFaceMakeupAdapter);
        mFaceMakeupAdapter.setItemSelected(0);
    }

    @Override
    public boolean isShown() {
        return isShown;
    }

    private void initViewBottomRadio() {
        mBottomCheckGroup = findViewById(R.id.beauty_radio_group);
        mBottomCheckGroup.setOnCheckedChangeListener(new CheckGroup.OnCheckedChangeListener() {
            int checkedidOld = View.NO_ID;

            @Override
            public void onCheckedChanged(CheckGroup group, int checkedId) {
                clickViewBottomRadio(checkedId);
                if ((checkedId == View.NO_ID || checkedId == checkedidOld) && checkedidOld != View.NO_ID) {
                    int endHeight = (int) getResources().getDimension(R.dimen.x98);
                    int startHeight = getHeight();
                    changeBottomLayoutAnimator(startHeight, endHeight);
                    isShown = false;
                } else if (checkedId != View.NO_ID && checkedidOld == View.NO_ID) {
                    int startHeight = (int) getResources().getDimension(R.dimen.x98);
                    int endHeight = (int) getResources().getDimension(R.dimen.x366);
                    changeBottomLayoutAnimator(startHeight, endHeight);
                    isShown = true;
                }
                checkedidOld = checkedId;
            }
        });
    }

    private void updateViewSkinBeauty() {
        mBoxSkinDetect.setVisibility(VISIBLE);
        mBoxHeavyBlur.setVisibility(VISIBLE);
        mBoxBlurLevel.setVisibility(GONE);
        mBoxEyeBright.setVisibility(VISIBLE);
        mBoxToothWhiten.setVisibility(VISIBLE);
        if (mOnFUControlListener != null) {
            mOnFUControlListener.onHeavyBlurSelected(sHeavyBlur);
        }
        onChangeFaceBeautyLevel(R.id.beauty_box_skin_detect);
        if (sHeavyBlur == 0) {
            onChangeFaceBeautyLevel(R.id.beauty_box_heavy_blur);
        } else {
            onChangeFaceBeautyLevel(R.id.beauty_box_blur_level);
        }
        onChangeFaceBeautyLevel(R.id.beauty_box_color_level);
        onChangeFaceBeautyLevel(R.id.beauty_box_red_level);
        onChangeFaceBeautyLevel(R.id.beauty_box_eye_bright);
        onChangeFaceBeautyLevel(R.id.beauty_box_tooth_whiten);
    }

    private void initViewSkinBeauty() {
        mSkinBeautySelect = findViewById(R.id.skin_beauty_select_block);

        mSkinBeautyBoxGroup = findViewById(R.id.beauty_group_skin_beauty);
        mSkinBeautyBoxGroup.setOnCheckedChangeListener(new BeautyBoxGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(BeautyBoxGroup group, int checkedId) {
                mFaceShapeLayout.setVisibility(GONE);
                mBeautySeekBar.setVisibility(GONE);
                if (checkedId != R.id.beauty_box_skin_detect) {
                    seekToSeekBar(checkedId);
                    onChangeFaceBeautyLevel(checkedId);
                }
            }
        });
        mBoxSkinDetect = findViewById(R.id.beauty_box_skin_detect);
        mBoxSkinDetect.setOnOpenChangeListener(new BeautyBox.OnOpenChangeListener() {
            @Override
            public void onOpenChanged(BeautyBox beautyBox, boolean isOpen) {
                sSkinDetect = isOpen ? 1 : 0;
                setDescriptionShowStr(sSkinDetect == 0 ? R.string.beauty_box_skin_detect_close : R.string.beauty_box_skin_detect_open);
                onChangeFaceBeautyLevel(R.id.beauty_box_skin_detect);
            }
        });
        mBoxHeavyBlur = findViewById(R.id.beauty_box_heavy_blur);
        mBoxHeavyBlur.setOnDoubleChangeListener(new BeautyBox.OnDoubleChangeListener() {
            @Override
            public void onDoubleChanged(BeautyBox beautyBox, boolean isDouble) {
                sHeavyBlur = isDouble ? 1 : 0;
                setDescriptionShowStr(sHeavyBlur == 0 ? R.string.beauty_box_heavy_blur_normal_text : R.string.beauty_box_heavy_blur_double_text);
                seekToSeekBar(R.id.beauty_box_heavy_blur);
                onChangeFaceBeautyLevel(R.id.beauty_box_heavy_blur);
                if (mOnFUControlListener != null) {
                    mOnFUControlListener.onHeavyBlurSelected(sHeavyBlur);
                }
            }
        });
        mBoxBlurLevel = findViewById(R.id.beauty_box_blur_level);
        mBoxEyeBright = findViewById(R.id.beauty_box_eye_bright);
        mBoxToothWhiten = findViewById(R.id.beauty_box_tooth_whiten);
    }

    private void updateViewFaceShape() {
        onChangeFaceBeautyLevel(R.id.beauty_box_eye_enlarge);
        onChangeFaceBeautyLevel(R.id.beauty_box_cheek_thinning);
        onChangeFaceBeautyLevel(R.id.beauty_box_cheek_v);
        onChangeFaceBeautyLevel(R.id.beauty_box_cheek_narrow);
        onChangeFaceBeautyLevel(R.id.beauty_box_cheek_small);
        onChangeFaceBeautyLevel(R.id.beauty_box_intensity_chin);
        onChangeFaceBeautyLevel(R.id.beauty_box_intensity_forehead);
        onChangeFaceBeautyLevel(R.id.beauty_box_intensity_nose);
        onChangeFaceBeautyLevel(R.id.beauty_box_intensity_mouth);
    }

    private void initViewFilterRecycler() {
        mFilterRecyclerView = findViewById(R.id.filter_recycle_view);
        mFilterRecyclerView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false));
        mFilterRecyclerView.setAdapter(mFilterRecyclerAdapter = new FilterRecyclerAdapter());
        ((SimpleItemAnimator) mFilterRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
    }

    private void updateViewFilterRecycler() {
        mFilterRecyclerAdapter.setFilter(sFilter);
        mOnFUControlListener.onFilterNameSelected(sFilter.filterName());
        float filterLevel = getFilterLevel(sFilter.filterName());
        mOnFUControlListener.onFilterLevelSelected(filterLevel);
    }

    private void initViewFaceShape() {
        mFlFaceShapeItems = findViewById(R.id.fl_face_shape_items);
        mIvRecoverFaceShpe = findViewById(R.id.iv_recover_face_shape);
        mIvRecoverFaceShpe.setOnClickListener(new OnMultiClickListener() {
            @Override
            protected void onMultiClick(View v) {
                // recover params
                setRecoverFaceShapeEnable(false);
                BeautyParameterModel.recoverToDefValue();
                updateViewFaceShape();
                int checkedId = mFaceShapeBeautyBoxGroup.getCheckedBeautyBoxId();
                seekToSeekBar(checkedId);
                setRecoverFaceShapeEnable(false);
            }
        });
        mTvRecoverFaceShpe = findViewById(R.id.tv_recover_face_shape);
        mFaceShapeBeautyBoxGroup = findViewById(R.id.beauty_group_face_shape);
        mFaceShapeBeautyBoxGroup.setOnCheckedChangeListener(new BeautyBoxGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(BeautyBoxGroup group, int checkedId) {
                mFaceShapeLayout.setVisibility(GONE);
                mBeautySeekBar.setVisibility(GONE);
                seekToSeekBar(checkedId);
                onChangeFaceBeautyLevel(checkedId);
            }
        });
        checkFaceShapeChanged();
    }

    private void setRecoverFaceShapeEnable(boolean enable) {
        if (enable) {
            mIvRecoverFaceShpe.setAlpha(1f);
            mTvRecoverFaceShpe.setAlpha(1f);
        } else {
            mIvRecoverFaceShpe.setAlpha(0.6f);
            mTvRecoverFaceShpe.setAlpha(0.6f);
        }
        mIvRecoverFaceShpe.setEnabled(enable);
        mTvRecoverFaceShpe.setEnabled(enable);
    }

    private void onChangeFaceBeautyLevel(int viewId) {
        if (viewId == View.NO_ID) {
            return;
        }
        ((BeautyBox) findViewById(viewId)).setOpen(isOpen(viewId));
        if (mOnFUControlListener == null) {
            return;
        }

        if (viewId == R.id.beauty_box_skin_detect) {
            mOnFUControlListener.onSkinDetectSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_heavy_blur) {
            mOnFUControlListener.onBlurLevelSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_blur_level) {
            mOnFUControlListener.onBlurLevelSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_color_level) {
            mOnFUControlListener.onColorLevelSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_red_level) {
            mOnFUControlListener.onRedLevelSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_eye_bright) {
            mOnFUControlListener.onEyeBrightSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_tooth_whiten) {
            mOnFUControlListener.onToothWhitenSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_eye_enlarge) {
            mOnFUControlListener.onEyeEnlargeSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_cheek_thinning) {
            mOnFUControlListener.onCheekThinningSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_cheek_narrow) {
            mOnFUControlListener.onCheekNarrowSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_cheek_v) {
            mOnFUControlListener.onCheekVSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_cheek_small) {
            mOnFUControlListener.onCheekSmallSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_intensity_chin) {
            mOnFUControlListener.onIntensityChinSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_intensity_forehead) {
            mOnFUControlListener.onIntensityForeheadSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_intensity_nose) {
            mOnFUControlListener.onIntensityNoseSelected(getValue(viewId));
        } else if (viewId == R.id.beauty_box_intensity_mouth) {
            mOnFUControlListener.onIntensityMouthSelected(getValue(viewId));
        }
    }

    private void initViewEffectRecycler() {
        mEffectRecyclerView = findViewById(R.id.fu_effect_recycler);
        mEffectRecyclerView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false));
        mEffectRecyclerView.setAdapter(mEffectRecyclerAdapter = new EffectRecyclerAdapter());
        ((SimpleItemAnimator) mEffectRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
    }


    private void initViewTop() {
        mFaceShapeLayout = findViewById(R.id.face_shape_radio_layout);
        mBeautySeekBar = findViewById(R.id.beauty_seek_bar);
        mBeautySeekBar.setOnProgressChangeListener(new DiscreteSeekBar.OnSimpleProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar SeekBar, int value, boolean fromUser) {
                if (!fromUser) {
                    return;
                }

                float valueF = 1.0f * (value - SeekBar.getMin()) / 100;
                int checkedCheckBoxId = mBottomCheckGroup.getCheckedCheckBoxId();
                if (checkedCheckBoxId == R.id.beauty_radio_skin_beauty) {
                    setValue(mSkinBeautyBoxGroup.getCheckedBeautyBoxId(), valueF);
                    onChangeFaceBeautyLevel(mSkinBeautyBoxGroup.getCheckedBeautyBoxId());
                } else if (checkedCheckBoxId == R.id.beauty_radio_face_shape) {
                    setValue(mFaceShapeBeautyBoxGroup.getCheckedBeautyBoxId(), valueF);
                    onChangeFaceBeautyLevel(mFaceShapeBeautyBoxGroup.getCheckedBeautyBoxId());
                    checkFaceShapeChanged();
                } else if (checkedCheckBoxId == R.id.beauty_radio_filter) {
                    mFilterRecyclerAdapter.setFilterLevels(valueF);
                } else if (checkedCheckBoxId == R.id.beauty_radio_face_beauty) {
                    // ??????????????????
                    float level = 1.0f * value / 100;
                    FaceMakeup faceMakeup = mFaceMakeupAdapter.getSelectedItems().valueAt(0);
                    String name = getResources().getString(faceMakeup.getNameId());
                    BeautyParameterModel.sBatchMakeupLevel.put(name, level);
                    List<MakeupItem> makeupItems = faceMakeup.getMakeupItems();
                    /* ????????????
                     * 0.4        0.7
                     * strength  level
                     * --> strength = 0.4 * level / 0.7
                     *   if level = 1.0, then strength = 0.57
                     *   if level = 0.2, then strength = 0.11
                     *   so, float strength = item.defaultLevel * level / DEFAULT_BATCH_MAKEUP_LEVEL
                     * */
                    if (makeupItems != null) {
                        for (MakeupItem makeupItem : makeupItems) {
                            float lev = makeupItem.getDefaultLevel() * level / FaceMakeupEnum.MAKEUP_OVERALL_LEVEL.get(faceMakeup.getNameId());
                            makeupItem.setLevel(lev);
                        }
                    }
                    mOnFUControlListener.onLightMakeupOverallLevelChanged(level);
                    mOnFUControlListener.onFilterLevelSelected(level);
                }
            }
        });
    }

    private void checkFaceShapeChanged() {
        if (BeautyParameterModel.checkIfFaceShapeChanged()) {
            setRecoverFaceShapeEnable(true);
        } else {
            setRecoverFaceShapeEnable(false);
        }
    }

    /**
     * ???????????? tab
     *
     * @param viewId
     */
    private void clickViewBottomRadio(int viewId) {
        mSkinBeautySelect.setVisibility(GONE);
        mFlFaceShapeItems.setVisibility(GONE);
        mFilterRecyclerView.setVisibility(GONE);
        mRvMakeupItems.setVisibility(GONE);
        mEffectRecyclerView.setVisibility(GONE);

        mFaceShapeLayout.setVisibility(GONE);
        mBeautySeekBar.setVisibility(GONE);
        if (viewId == R.id.beauty_radio_skin_beauty) {
            mSkinBeautySelect.setVisibility(VISIBLE);
            int id = mSkinBeautyBoxGroup.getCheckedBeautyBoxId();
            if (id != R.id.beauty_box_skin_detect) {
                seekToSeekBar(id);
            }
        } else if (viewId == R.id.beauty_radio_face_shape) {
            mFlFaceShapeItems.setVisibility(VISIBLE);
            int id = mFaceShapeBeautyBoxGroup.getCheckedBeautyBoxId();
            seekToSeekBar(id);
        } else if (viewId == R.id.beauty_radio_filter) {
            mFilterRecyclerView.setVisibility(VISIBLE);
            mFilterRecyclerAdapter.setFilterProgress();
        } else if (viewId == R.id.beauty_radio_face_beauty) {
            mRvMakeupItems.setVisibility(VISIBLE);
            mBeautySeekBar.setVisibility(INVISIBLE);

            FaceMakeup faceMakeup = mFaceMakeupAdapter.getSelectedItems().valueAt(0);
            if (faceMakeup != null) {
                String name = getResources().getString(faceMakeup.getNameId());
                Float level = BeautyParameterModel.sBatchMakeupLevel.get(name);
                if (level == null) {
                    level = FaceMakeupEnum.MAKEUP_OVERALL_LEVEL.get(faceMakeup.getNameId());
                    BeautyParameterModel.sBatchMakeupLevel.put(name, level);
                }
                if (level != null) {
                    seekToSeekBar(level);
                }
            }
        } else if (viewId == R.id.beauty_radio_stickers) {
            mBeautySeekBar.setVisibility(INVISIBLE);
            mEffectRecyclerView.setVisibility(VISIBLE);

        }
    }

    private void seekToSeekBar(float value) {
        seekToSeekBar(value, 0, 100);
    }

    private void seekToSeekBar(float value, int min, int max) {
        mBeautySeekBar.setVisibility(VISIBLE);
        mBeautySeekBar.setMin(min);
        mBeautySeekBar.setMax(max);
        mBeautySeekBar.setProgress((int) (value * (max - min) + min));
    }

    private void seekToSeekBar(int checkedId) {
        if (checkedId == View.NO_ID) {
            return;
        }

        float value = getValue(checkedId);
        int min = 0;
        int max = 100;
        if (checkedId == R.id.beauty_box_intensity_chin || checkedId == R.id.beauty_box_intensity_forehead || checkedId == R.id.beauty_box_intensity_mouth) {
            min = -50;
            max = 50;
        }
        seekToSeekBar(value, min, max);
    }

    private void changeBottomLayoutAnimator(final int startHeight, final int endHeight) {
        if (mBottomLayoutAnimator != null && mBottomLayoutAnimator.isRunning()) {
            mBottomLayoutAnimator.end();
        }
        mBottomLayoutAnimator = ValueAnimator.ofInt(startHeight, endHeight).setDuration(150);
        mBottomLayoutAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int height = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams params = getLayoutParams();
                if (params == null) {
                    return;
                }
                params.height = height;
                setLayoutParams(params);
                if (mOnBottomAnimatorChangeListener != null) {
                    float showRate = 1.0f * (height - startHeight) / (endHeight - startHeight);
                    mOnBottomAnimatorChangeListener.onBottomAnimatorChangeListener(startHeight > endHeight ? 1 - showRate : showRate);
                }
            }
        });
        mBottomLayoutAnimator.start();
    }

    public interface OnBottomAnimatorChangeListener {
        void onBottomAnimatorChangeListener(float showRate);
    }

    public void setOnBottomAnimatorChangeListener(OnBottomAnimatorChangeListener onBottomAnimatorChangeListener) {
        mOnBottomAnimatorChangeListener = onBottomAnimatorChangeListener;
    }

    private OnBottomAnimatorChangeListener mOnBottomAnimatorChangeListener;

    private ValueAnimator mBottomLayoutAnimator;

    private void setDescriptionShowStr(int str) {
        if (mOnDescriptionShowListener != null) {
            mOnDescriptionShowListener.onDescriptionShowListener(str);
        }
    }

    public void hideBottomLayoutAnimator() {
        mBottomCheckGroup.check(View.NO_ID);
    }

    public interface OnDescriptionShowListener {
        void onDescriptionShowListener(int str);
    }

    public void setOnDescriptionShowListener(OnDescriptionShowListener onDescriptionShowListener) {
        mOnDescriptionShowListener = onDescriptionShowListener;
    }

    private OnDescriptionShowListener mOnDescriptionShowListener;

    public void setFilterLevel(String filterName, float faceBeautyFilterLevel) {
        sFilterLevel.put(STR_FILTER_LEVEL + filterName, faceBeautyFilterLevel);
        if (mOnFUControlListener != null) {
            mOnFUControlListener.onFilterLevelSelected(faceBeautyFilterLevel);
        }
    }

    public float getFilterLevel(String filterName) {
        String key = STR_FILTER_LEVEL + filterName;
        Float level = sFilterLevel.get(key);
        if (level == null) {
            level = Filter.DEFAULT_FILTER_LEVEL;
            sFilterLevel.put(key, level);
        }
        setFilterLevel(filterName, level);
        return level;
    }

    class FilterRecyclerAdapter extends RecyclerView.Adapter<FilterRecyclerAdapter.HomeRecyclerHolder> {

        @Override
        public HomeRecyclerHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new HomeRecyclerHolder(LayoutInflater.from(mContext).inflate(R.layout.layout_beauty_control_recycler, parent, false));
        }

        @Override
        public void onBindViewHolder(HomeRecyclerHolder holder, final int position) {
            final List<Filter> filters = mFilters;
            holder.filterImg.setImageResource(filters.get(position).resId());
            holder.filterName.setText(filters.get(position).description());
            if (mFilterPositionSelect == position) {
                holder.filterImg.setBackgroundResource(R.drawable.control_filter_select);
            } else {
                holder.filterImg.setBackgroundResource(0);
            }
            holder.itemView.setOnClickListener(new OnMultiClickListener() {
                @Override
                protected void onMultiClick(View v) {
                    mFilterPositionSelect = position;
                    mBeautySeekBar.setVisibility(position == 0 ? INVISIBLE : VISIBLE);
                    setFilterProgress();
                    notifyDataSetChanged();
                    if (mOnFUControlListener != null) {
                        sFilter = filters.get(mFilterPositionSelect);
                        mOnFUControlListener.onFilterNameSelected(sFilter.filterName());
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mFilters.size();
        }

        public void setFilterLevels(float filterLevels) {
            if (mFilterPositionSelect >= 0) {
                setFilterLevel(mFilters.get(mFilterPositionSelect).filterName(), filterLevels);
            }
        }

        public void setFilter(Filter filter) {
            mFilterPositionSelect = mFilters.indexOf(filter);
        }

        public int indexOf(Filter filter) {
            for (int i = 0; i < mFilters.size(); i++) {
                if (filter.filterName().equals(mFilters.get(i).filterName())) {
                    return i;
                }
            }
            return -1;
        }

        public void setFilterProgress() {
            if (mFilterPositionSelect >= 0) {
                seekToSeekBar(getFilterLevel(mFilters.get(mFilterPositionSelect).filterName()));
            }
        }

        class HomeRecyclerHolder extends RecyclerView.ViewHolder {

            ImageView filterImg;
            TextView filterName;

            public HomeRecyclerHolder(View itemView) {
                super(itemView);
                filterImg = (ImageView) itemView.findViewById(R.id.control_recycler_img);
                filterName = (TextView) itemView.findViewById(R.id.control_recycler_text);
            }
        }
    }

    // ----------- ?????????????????????????????????????????????

    // ????????????????????????
    private class OnFaceMakeupClickListener implements BaseRecyclerAdapter.OnItemClickListener<FaceMakeup> {

        @Override
        public void onItemClick(BaseRecyclerAdapter<FaceMakeup> adapter, View view, int position) {
            FaceMakeup faceMakeup = adapter.getItem(position);
            if (position == 0) {
                // ??????
                mBeautySeekBar.setVisibility(View.INVISIBLE);
                int old = mFilterPositionSelect;
                mFilterPositionSelect = 0;
                mFilterRecyclerAdapter.notifyItemChanged(old);
                mOnFUControlListener.onLightMakeupBatchSelected(faceMakeup.getMakeupItems());
            } else {
                // ????????????
                mBeautySeekBar.setVisibility(View.INVISIBLE);
                String name = getResources().getString(faceMakeup.getNameId());
                Float level = BeautyParameterModel.sBatchMakeupLevel.get(name);
                boolean used = true;
                if (level == null) {
                    used = false;
                    level = FaceMakeupEnum.MAKEUP_OVERALL_LEVEL.get(faceMakeup.getNameId());
                    BeautyParameterModel.sBatchMakeupLevel.put(name, level);
                }
                seekToSeekBar(level);

                mOnFUControlListener.onLightMakeupBatchSelected(faceMakeup.getMakeupItems());
                mOnFUControlListener.onLightMakeupOverallLevelChanged(level);

                Pair<Filter, Float> filterFloatPair = FaceMakeupEnum.MAKEUP_FILTERS.get(faceMakeup.getNameId());
                if (filterFloatPair != null) {
                    // ????????????????????????????????????????????????
                    Filter filter = filterFloatPair.first;
                    int i = mFilterRecyclerAdapter.indexOf(filter);
                    if (i >= 0) {
                        mFilterPositionSelect = i;
                        mFilterRecyclerAdapter.notifyItemChanged(i);
                        mFilterRecyclerView.scrollToPosition(i);
                    } else {
                        int old = mFilterPositionSelect;
                        mFilterPositionSelect = -1;
                        mFilterRecyclerAdapter.notifyItemChanged(old);
                    }
                    mOnFUControlListener.onFilterNameSelected(filter.filterName());
                    Float filterLevel = used ? level : filterFloatPair.second;
                    sFilter = filter;
                    String filterName = filter.filterName();
                    sFilterLevel.put(STR_FILTER_LEVEL + filterName, filterLevel);
                    setFilterLevel(filterName, filterLevel);
                }
            }
        }
    }

    // ?????????????????????
    private class FaceMakeupAdapter extends BaseRecyclerAdapter<FaceMakeup> {

        FaceMakeupAdapter(@NonNull List<FaceMakeup> data) {
            super(data, R.layout.layout_rv_makeup);
        }

        @Override
        protected void bindViewHolder(BaseViewHolder viewHolder, FaceMakeup item) {
            viewHolder.setText(R.id.tv_makeup, getResources().getString(item.getNameId()))
                    .setImageResource(R.id.iv_makeup, item.getIconId());
        }

        @Override
        protected void handleSelectedState(BaseViewHolder viewHolder, FaceMakeup data, boolean selected) {
            ((TextView) viewHolder.getViewById(R.id.tv_makeup)).setTextColor(selected ?
                    getResources().getColor(R.color.main_color) : getResources().getColor(R.color.colorWhite));
            viewHolder.setBackground(R.id.iv_makeup, selected ? R.drawable.control_filter_select : 0);
        }
    }

    class EffectRecyclerAdapter extends RecyclerView.Adapter<EffectRecyclerAdapter.HomeRecyclerHolder> {

        private List<Effect> mEffects;
        private int mPositionSelect = 0;
//        private EffectRecyclerAdapter.OnDescriptionChangeListener mOnDescriptionChangeListener;

        public EffectRecyclerAdapter() {
            mEffects = EffectEnum.getEffectsByEffectType(mEffectType);
        }

        @Override

        public HomeRecyclerHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new HomeRecyclerHolder(LayoutInflater.from(mContext).inflate(R.layout.layout_beauty_control_recycler, parent, false));
        }

        @Override
        public void onBindViewHolder(HomeRecyclerHolder holder, final int position) {

            holder.effectImg.setImageResource(mEffects.get(position).resId());
            holder.effectName.setText(mEffects.get(position).bundleName());
            holder.effectImg.setOnClickListener(new OnMultiClickListener() {
                @Override
                protected void onMultiClick(View v) {
                    if (mPositionSelect == position) {
                        return;
                    }
                    Effect click = mEffects.get(mPositionSelect = position);
                    mOnFUControlListener.onEffectSelected(click);

                    notifyDataSetChanged();
//                    if (mOnDescriptionChangeListener != null)
//                        mOnDescriptionChangeListener.onDescriptionChangeListener(click.description());
                }
            });
            if (mPositionSelect == position) {
                holder.effectImg.setBackgroundResource(R.drawable.effect_select);
            } else {
                holder.effectImg.setBackgroundResource(0);
            }
        }

        @Override
        public int getItemCount() {
            return mEffects.size();
        }

        class HomeRecyclerHolder extends RecyclerView.ViewHolder {

            ImageView effectImg;
            TextView effectName;

            public HomeRecyclerHolder(View itemView) {
                super(itemView);
                effectImg = (ImageView) itemView.findViewById(R.id.control_recycler_img);
                effectName = (TextView) itemView.findViewById(R.id.control_recycler_text);
            }
        }

        public Effect getSelectEffect() {
            return mEffects.get(mPositionSelect);
        }

    }
}