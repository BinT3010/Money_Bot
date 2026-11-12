package com.moneyset.app;

import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Budget form, server warning states and limit suggestions with their basis.
 * Every amount, percentage, level and message shown here comes from the API; the screen computes no totals. */
final class BudgetScreen {
    // Temporary UI range until the backend returns category-specific bounds.
    private static final int PLACEHOLDER_RANGE_MAX_BUDGET_PERCENT=50;

    static void budget(MainActivity host) {
        host.monthPicker();
        LinearLayout states = host.column(); host.content.addView(states);
        host.api.call("GET","/budgets/"+host.month,null,r->{
            JSONObject b=(JSONObject)r, limits=b.getJSONObject("limits");
            EditText total=host.input(host.content,"Ngân sách tổng VND",b.isNull("total")?"":b.getString("total"),true);
            total.setTextColor(UiHelpers.FUNDS_GREEN);
            EditText threshold=host.input(host.content,"Cảnh báo sớm (%)",b.getString("threshold"),true);
            TextView thresholdValue=host.text(host.content,"Cảnh báo từ "+b.getInt("threshold")+"%",13);
            SeekBar thresholdSlider=new SeekBar(host);styleBudgetSlider(host,thresholdSlider,UiHelpers.BRAND);
            thresholdSlider.setMax(98);thresholdSlider.setProgress(Math.max(0,Math.min(98,b.getInt("threshold")-1)));
            host.content.addView(thresholdSlider,new LinearLayout.LayoutParams(-1,host.dp(48)));
            thresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){if(fromUser){int value=progress+1;threshold.setText(String.valueOf(value));thresholdValue.setText("Cảnh báo từ "+value+"%");}}
                @Override public void onStartTrackingTouch(SeekBar seekBar){}
                @Override public void onStopTrackingTouch(SeekBar seekBar){}
            });
            threshold.addTextChangedListener(new android.text.TextWatcher(){
                @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
                @Override public void onTextChanged(CharSequence text,int start,int before,int count){
                    try { int value=Integer.parseInt(text.toString().replace(",", ""));
                        thresholdSlider.setProgress(Math.max(0,Math.min(98,value-1)));
                        thresholdValue.setText("Cảnh báo từ "+value+"%");
                    } catch(NumberFormatException ignored) { }
                }
                @Override public void afterTextChanged(android.text.Editable text){}
            });
            EditText[] edits=new EditText[6];SeekBar[] limitSliders=new SeekBar[6];TextView[] allocationWarnings=new TextView[6];
            long[] lastAllocation=new long[6];
            TextView[] recommendationLabels=new TextView[6];ProgressBar[] recommendationBars=new ProgressBar[6];
            long[] recommended=new long[6],rangeMinimum=new long[6],rangeMaximum=new long[6];
            boolean[] hasRecommendation=new boolean[6],hasRange=new boolean[6];
            boolean[] baseCaseActive={limits.length()==0},applyingBaseCase={false};
            android.animation.ValueAnimator[] warningPulses=new android.animation.ValueAnimator[6];
            host.text(host.content,"Để trống: chưa đặt hạn mức. Nhập 0: không dành ngân sách cho danh mục.",14);
            TextView allocationSummary=host.text(host.content,"",13);
            for(int i=0;i<6;i++){
                final int index=i;edits[i]=host.input(host.content,host.labels[i],limits.optString(host.categories[i]),true);
                lastAllocation[i]=amountOrZero(host.value(edits[i]));
                recommendationLabels[i]=host.text(host.content,"",12);
                recommendationLabels[i].setVisibility(android.view.View.GONE);
                recommendationBars[i]=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);
                recommendationBars[i].setMax(100);
                recommendationBars[i].setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(14,165,233)));
                recommendationBars[i].setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(224,242,254)));
                recommendationBars[i].setVisibility(android.view.View.GONE);
                LinearLayout.LayoutParams recommendationParams=new LinearLayout.LayoutParams(-1,host.dp(7));
                recommendationParams.bottomMargin=host.dp(2);
                host.content.addView(recommendationBars[i],recommendationParams);
                TextView allocationWarning=host.text(host.content,"Quá nhiều!",13);allocationWarnings[i]=allocationWarning;
                allocationWarning.setTextColor(UiHelpers.EXPENSE_RED);allocationWarning.setTypeface(null,android.graphics.Typeface.BOLD);
                allocationWarning.setVisibility(android.view.View.GONE);
                SeekBar limitSlider=new SeekBar(host);limitSliders[i]=limitSlider;styleBudgetSlider(host,limitSlider,UiHelpers.BRAND);limitSlider.setMax(100);
                long cap=b.isNull("total")?0:b.getLong("total");
                hasRange[i]=cap>0;rangeMinimum[i]=0;rangeMaximum[i]=cap*PLACEHOLDER_RANGE_MAX_BUDGET_PERCENT/100;
                boolean configured=limits.has(host.categories[i]);long saved=configured?limits.getLong(host.categories[i]):0;
                limitSlider.setProgress(cap>0?(int)Math.max(0,Math.min(100,saved*100/cap)):0);
                limitSlider.setEnabled(cap>0);limitSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(UiHelpers.BRAND));
                limitSlider.setContentDescription(host.labels[i]+" · kéo để chỉnh hạn mức trong tổng ngân sách");
                host.content.addView(limitSlider,new LinearLayout.LayoutParams(-1,host.dp(48)));
                limitSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                        long currentCap=amountOrZero(host.value(total));
                        if(fromUser&&currentCap>0){
                            long value=currentCap*progress/100;
                            long available=availableAllocation(host,edits,index,currentCap);
                            if(value>lastAllocation[index])value=Math.min(value,available);
                            edits[index].setText(String.valueOf(value));
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar bar){}
                    @Override public void onStopTrackingTouch(SeekBar bar){}
                });
                edits[i].addTextChangedListener(new android.text.TextWatcher(){
                    @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
                    @Override public void onTextChanged(CharSequence text,int start,int before,int count){
                        if(!applyingBaseCase[0])baseCaseActive[0]=false;
                        long currentCap=amountOrZero(host.value(total));
                        long value=amountOrZero(text.toString());
                        long available=availableAllocation(host,edits,index,currentCap);
                        if(!applyingBaseCase[0]&&value>lastAllocation[index]&&value>available){
                            edits[index].setText(String.valueOf(available));
                            return;
                        }
                        lastAllocation[index]=value;
                        limitSlider.setProgress(currentCap>0?(int)Math.max(0,Math.min(100,value*100/currentCap)):0);
                        if(hasRange[index])
                            setAllocationWarning(limitSlider,warningPulses,allocationWarning,index,outsideRange(value,rangeMinimum[index],rangeMaximum[index]));
                        if(hasRecommendation[index]){
                            int recommendationProgress=currentCap>0?(int)Math.max(0,Math.min(100,recommended[index]*100/currentCap)):0;
                            recommendationBars[index].setProgress(recommendationProgress);
                            recommendationLabels[index].setText("Mức gợi ý · "+host.money(recommended[index]));
                        }
                        updateAllocationSummary(host,total,edits,allocationSummary);
                    }
                    @Override public void afterTextChanged(android.text.Editable text){}
                });
                limitSlider.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener(){
                    @Override public void onViewAttachedToWindow(android.view.View view){
                        if(hasRange[index])setAllocationWarning(limitSlider,warningPulses,allocationWarning,index,
                            outsideRange(amountOrZero(host.value(edits[index])),rangeMinimum[index],rangeMaximum[index]));
                    }
                    @Override public void onViewDetachedFromWindow(android.view.View view){
                        if(warningPulses[index]!=null)warningPulses[index].cancel();
                    }
                });
                if(hasRange[i])setAllocationWarning(limitSlider,warningPulses,allocationWarning,i,
                    outsideRange(saved,rangeMinimum[i],rangeMaximum[i]));
            }
            total.addTextChangedListener(new android.text.TextWatcher(){
                @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
                @Override public void onTextChanged(CharSequence text,int start,int before,int count){
                    long cap=amountOrZero(text.toString());
                    if(baseCaseActive[0]&&cap>0)fillBaseCase(cap,edits,applyingBaseCase);
                    for(int i=0;i<limitSliders.length;i++){
                        limitSliders[i].setEnabled(cap>0);
                        long value=amountOrZero(host.value(edits[i]));
                        hasRange[i]=cap>0;rangeMinimum[i]=0;
                        rangeMaximum[i]=cap*PLACEHOLDER_RANGE_MAX_BUDGET_PERCENT/100;
                        limitSliders[i].setProgress(cap>0?(int)Math.max(0,Math.min(100,value*100/cap)):0);
                        setAllocationWarning(limitSliders[i],warningPulses,allocationWarnings[i],i,
                            hasRange[i]&&outsideRange(value,rangeMinimum[i],rangeMaximum[i]));
                        if(hasRecommendation[i]){
                            recommendationBars[i].setProgress(cap>0?(int)Math.max(0,Math.min(100,recommended[i]*100/cap)):0);
                        }
                    }
                    updateAllocationSummary(host,total,edits,allocationSummary);
                }
                @Override public void afterTextChanged(android.text.Editable text){}
            });
            long initialCap=amountOrZero(host.value(total));
            if(baseCaseActive[0]&&initialCap>0)fillBaseCase(initialCap,edits,applyingBaseCase);
            updateAllocationSummary(host,total,edits,allocationSummary);
            host.button(host.content,"Xác nhận lưu ngân sách",()->{
                long cap=amountOrZero(host.value(total));
                if(sumAllocations(host,edits)>cap){
                    UiHelpers.warningStatus(host,"Tổng hạn mức danh mục vượt ngân sách tổng. Giảm một hoặc nhiều khoản trước khi lưu.");
                    return;
                }
                JSONObject values=new JSONObject();for(int i=0;i<6;i++)if(!host.value(edits[i]).isEmpty())values.put(host.categories[i],host.amount(edits[i]));
                host.api.call("PUT","/budgets/"+host.month,new JSONObject().put("total",host.amount(total)).put("threshold",Integer.parseInt(host.value(threshold))).put("limits",values),v->{
                    host.load(); UiHelpers.status(host,"Đã lưu ngân sách tháng "+host.month+".");
                },host::error);
            });
            LinearLayout basis=host.column();
            host.button(host.content,"Gợi ý từ 3 tháng trước",()->host.api.call("GET","/budgets/"+host.month+"/suggestions",null,v->{
                JSONObject result=(JSONObject)v;
                boolean available=result.optBoolean("available");
                if(available){
                    JSONObject suggested=result.getJSONObject("limits");
                    long currentCap=amountOrZero(host.value(total));
                    applyingBaseCase[0]=true;
                    baseCaseActive[0]=false;
                    try{
                        for(int i=0;i<6;i++){
                            recommended[i]=suggested.getLong(host.categories[i]);hasRecommendation[i]=true;
                            recommendationLabels[i].setText("Mức gợi ý · "+host.money(recommended[i]));
                            recommendationLabels[i].setVisibility(android.view.View.VISIBLE);
                            recommendationBars[i].setProgress(currentCap>0?(int)Math.max(0,Math.min(100,recommended[i]*100/currentCap)):0);
                            recommendationBars[i].setVisibility(android.view.View.VISIBLE);
                            edits[i].setText(suggested.getString(host.categories[i]));
                        }
                    }finally{applyingBaseCase[0]=false;}
                    UiHelpers.status(host,"Đã điền hạn mức gợi ý. Chưa lưu; kiểm tra rồi bấm Xác nhận lưu ngân sách.");
                } else {
                    for(int i=0;i<6;i++){
                        hasRecommendation[i]=false;
                        recommendationLabels[i].setVisibility(android.view.View.GONE);
                        recommendationBars[i].setVisibility(android.view.View.GONE);
                    }
                    UiHelpers.warningStatus(host,"Chưa có gợi ý; hãy nhập hạn mức thủ công.");
                }
                suggestion(host,basis,result);
            },host::error));
            host.content.addView(basis);
            host.api.call("GET","/summary?month="+host.month,null,s->states(host,states,(JSONObject)s,edits),host::error);
        },host::error);
    }

    /** Ou4 states for the month: unset vs zero limits, exact 100%, custom threshold and overspending, all decided by the server. */
    static void states(MainActivity host, LinearLayout box, JSONObject s, EditText[] edits) throws Exception {
        box.removeAllViews();
        JSONObject budget=s.getJSONObject("budget");
        host.text(box,"Trạng thái hạn mức tháng "+s.getString("month")+" · cảnh báo sớm ở "+budget.getInt("threshold")+"%",16);
        if(budget.isNull("total")) host.text(box,"Chưa đặt ngân sách tổng tháng này.",14);
        else {
            long remaining=s.getLong("remaining");
            box.addView(row(host,UiHelpers.moneyLabel(host,"Ngân sách tổng: ",budget.getLong("total"),"",false)));
            box.addView(row(host,UiHelpers.moneyLabel(host,"Đã chi tháng: ",s.getLong("total"),"",true)));
            TextView left=host.text(box,"",14);
            if(remaining<0){left.setText(UiHelpers.moneyLabel(host,"Vượt ngân sách tổng: ",-remaining,"",true));UiHelpers.warningStyle(host,left);}
            else left.setText(UiHelpers.moneyLabel(host,"Ngân sách tổng còn: ",remaining,"",false));
        }
        JSONArray rows=s.getJSONArray("categories");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String level=row.getString("level");
            String compact=row.getString("label")+" · "+host.money(row.getLong("spent"));
            if(!row.isNull("limit")){
                compact+=" / "+host.money(row.getLong("limit"));
                if(!row.isNull("percent"))compact+=" ("+String.format(Locale.ROOT,"%.0f%%",row.getDouble("percent"))+ ")";
            }
            TextView line=host.text(box,"●  "+compact,14);
            line.setTextColor(warns(level)?UiHelpers.EXPENSE_RED:level.equals("UNSET")?UiHelpers.NEUTRAL:UiHelpers.INK);
            line.setContentDescription((row.isNull("message")?"":row.getString("message"))+" "+level);
            int editIndex=i;
            line.setClickable(true);line.setOnClickListener(v->{edits[editIndex].requestFocus();host.content.requestChildFocus(edits[editIndex],edits[editIndex]);});
            ProgressBar usage=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);usage.setMax(100);
            double ratio=row.isNull("ratio")?0:row.optDouble("ratio",0);
            if(row.isNull("ratio")&&level.equals("OVER")&&row.getLong("spent")>0)ratio=1;
            usage.setProgress((int)Math.max(0,Math.min(100,Math.round(ratio*100))));
            int color=warns(level)?UiHelpers.EXPENSE_RED:UiHelpers.FUNDS_GREEN;
            usage.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            usage.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(226,232,240)));
            usage.setContentDescription(row.getString("label")+" · "+level+" · chạm để sửa hạn mức");
            usage.setClickable(true);usage.setOnClickListener(v->{edits[editIndex].requestFocus();host.content.requestChildFocus(edits[editIndex],edits[editIndex]);});
            LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,host.dp(12));progressParams.bottomMargin=host.dp(8);box.addView(usage,progressParams);
        }
    }

    static boolean warns(String level) { return level.equals("WARNING")||level.equals("REACHED")||level.equals("OVER"); }

    private static long amountOrZero(String value){try{return Long.parseLong(value.replace(",",""));}catch(NumberFormatException ignored){return 0;}}

    private static boolean outsideRange(long value,long minimum,long maximum){return value<minimum||value>maximum;}

    private static void styleBudgetSlider(MainActivity host,SeekBar slider,int progressColor){
        android.graphics.drawable.GradientDrawable track=new android.graphics.drawable.GradientDrawable();
        track.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        track.setColor(android.graphics.Color.rgb(30,41,59));
        track.setCornerRadius(host.dp(4));track.setStroke(host.dp(1),android.graphics.Color.rgb(71,85,105));
        track.setSize(host.dp(1),host.dp(7));
        android.graphics.drawable.GradientDrawable fill=new android.graphics.drawable.GradientDrawable();
        fill.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        fill.setColor(progressColor);fill.setCornerRadius(host.dp(4));fill.setSize(host.dp(1),host.dp(7));
        android.graphics.drawable.ClipDrawable clipped=new android.graphics.drawable.ClipDrawable(fill,android.view.Gravity.LEFT,android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable progress=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{track,clipped});
        progress.setId(0,android.R.id.background);progress.setId(1,android.R.id.progress);slider.setProgressDrawable(progress);
        for(int i=0;i<2;i++){
            progress.setLayerHeight(i,host.dp(7));
            progress.setLayerGravity(i,android.view.Gravity.FILL_HORIZONTAL|android.view.Gravity.CENTER_VERTICAL);
        }
        android.graphics.drawable.GradientDrawable thumb=new android.graphics.drawable.GradientDrawable();
        thumb.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        thumb.setColor(android.graphics.Color.WHITE);
        thumb.setCornerRadius(host.dp(8));
        thumb.setStroke(host.dp(2),UiHelpers.BRAND);thumb.setSize(host.dp(24),host.dp(16));
        slider.setThumb(thumb);slider.setThumbOffset(host.dp(8));
        slider.setMinimumHeight(host.dp(40));slider.setPadding(host.dp(8),host.dp(8),host.dp(8),host.dp(8));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(progressColor));
    }

    private static void fillBaseCase(long total,EditText[] edits,boolean[] applying){
        applying[0]=true;
        try{
            String share=String.valueOf(total/5);
            for(int i=0;i<5;i++)edits[i].setText(share);
            edits[5].setText("0");
        }finally{applying[0]=false;}
    }

    private static long sumAllocations(MainActivity host,EditText[] edits){
        long total=0;
        for(EditText edit:edits){long value=amountOrZero(host.value(edit));if(value>0)total+=value;}
        return total;
    }

    private static long availableAllocation(MainActivity host,EditText[] edits,int excluded,long cap){
        long other=0;
        for(int i=0;i<edits.length;i++)if(i!=excluded){long value=amountOrZero(host.value(edits[i]));if(value>0)other+=value;}
        return Math.max(0,cap-other);
    }

    private static void updateAllocationSummary(MainActivity host,EditText total,EditText[] edits,TextView summary){
        long cap=amountOrZero(host.value(total)),allocated=sumAllocations(host,edits);
        if(allocated>cap){
            summary.setText("Đã phân bổ "+host.money(allocated)+" / "+host.money(cap)+" · vượt ngân sách");
            summary.setTextColor(UiHelpers.EXPENSE_RED);
        }else{
            summary.setText("Đã phân bổ "+host.money(allocated)+" / "+host.money(cap)+" · còn "+host.money(cap-allocated));
            summary.setTextColor(UiHelpers.FUNDS_GREEN);
        }
    }

    private static void setAllocationWarning(SeekBar slider,android.animation.ValueAnimator[] pulses,TextView warning,int index,boolean unreasonable){
        warning.setVisibility(unreasonable?android.view.View.VISIBLE:android.view.View.GONE);
        slider.setContentDescription(unreasonable
            ?"Hạn mức ngoài khoảng thử nghiệm 0–"+PLACEHOLDER_RANGE_MAX_BUDGET_PERCENT+"% ngân sách tổng · cảnh báo đỏ"
            :"Hạn mức trong khoảng thử nghiệm 0–"+PLACEHOLDER_RANGE_MAX_BUDGET_PERCENT+"% ngân sách tổng · xanh dương");
        if(unreasonable){
            if(pulses[index]==null){
                android.animation.ValueAnimator pulse=android.animation.ValueAnimator.ofFloat(0f,1f);
                pulse.setDuration(700);pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                pulse.addUpdateListener(animation->{
                    float t=(Float)animation.getAnimatedValue();
                    int red=android.graphics.Color.rgb(185,28,28),bright=android.graphics.Color.rgb(239,68,68);
                    slider.setProgressTintList(android.content.res.ColorStateList.valueOf(blend(red,bright,t)));
                    slider.setThumbTintList(android.content.res.ColorStateList.valueOf(blend(red,bright,t)));
                });
                pulses[index]=pulse;
            }
            if(!pulses[index].isStarted())pulses[index].start();
        }else{
            if(pulses[index]!=null)pulses[index].cancel();
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(UiHelpers.BRAND));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(UiHelpers.BRAND));
        }
    }

    private static int blend(int from,int to,float fraction){
        int red=Math.round(android.graphics.Color.red(from)+(android.graphics.Color.red(to)-android.graphics.Color.red(from))*fraction);
        int green=Math.round(android.graphics.Color.green(from)+(android.graphics.Color.green(to)-android.graphics.Color.green(from))*fraction);
        int blue=Math.round(android.graphics.Color.blue(from)+(android.graphics.Color.blue(to)-android.graphics.Color.blue(from))*fraction);
        return android.graphics.Color.rgb(red,green,blue);
    }

    private static TextView row(MainActivity host, CharSequence value) {
        TextView v=new TextView(host); v.setText(value); v.setTextSize(14); return v;
    }

    /** Suggestion basis (G2/2.1): months, per-month history, averages, budget B, factor B/A, allocated and unallocated. */
    static void suggestion(MainActivity host, LinearLayout box, JSONObject r) throws Exception {
        box.removeAllViews();
        JSONArray months=r.getJSONArray("months");
        String span=months.getString(0)+" – "+months.getString(months.length()-1);
        if(!r.optBoolean("available")){
            UiHelpers.warning(host,box,"Không gợi ý được: "+r.getString("reason"),14);
            JSONArray missing=r.optJSONArray("missing");
            if(missing!=null)for(int i=0;i<missing.length();i++){
                String m=missing.getString(i);
                host.text(box,"Thiếu: "+(m.equals("budget")?"ngân sách tổng đã lưu cho tháng "+host.month
                    :m.equals("history")?"lịch sử chi bắt đầu từ "+months.getString(0)+" trở về trước":m),14);
            }
            if(r.has("history"))history(host,box,r,months,null);
            return;
        }
        host.text(box,"Gợi ý hạn mức (chưa lưu) · căn cứ "+span,16);
        host.text(box,r.getString("reason"),14);
        history(host,box,r,months,r.getJSONObject("averages"));
        JSONObject limits=r.getJSONObject("limits");
        for(int i=0;i<6;i++){
            String cid=host.categories[i];
            host.text(box,"Gợi ý "+host.labels[i]+": "+host.money(limits.getLong(cid)),14);
        }
        host.text(box,"Trung bình tổng A: "+average(host,r.getDouble("average_total"))+" · ngân sách B: "+host.money(r.getLong("budget_total"))
            +" · hệ số: "+factor(r.getDouble("factor"))+(r.getDouble("factor")<1?" (đã thu nhỏ theo B/A)":" (giữ nguyên trung bình)"),14);
        host.text(box,"Đã phân bổ: "+host.money(r.getLong("allocated"))+" · chưa phân bổ: "+host.money(r.getLong("unallocated")),14);
        LinearLayout actions=new LinearLayout(host);actions.setGravity(android.view.Gravity.RIGHT);box.addView(actions);
        Button discard=host.button(actions,"×",host::load);discard.setTextSize(22);discard.setContentDescription("Bỏ gợi ý, giữ hạn mức đã lưu");
        discard.setMinWidth(0);discard.setMinimumWidth(0);discard.setLayoutParams(new LinearLayout.LayoutParams(host.dp(44),host.dp(44)));
    }

    private static void history(MainActivity host, LinearLayout box, JSONObject r, JSONArray months, JSONObject averages) throws Exception {
        JSONObject history=r.getJSONObject("history");
        TableLayout table=new TableLayout(host); table.setStretchAllColumns(true);
        TableRow head=new TableRow(host); cell(host,head,"Danh mục");
        for(int m=0;m<months.length();m++)cell(host,head,months.getString(m));
        if(averages!=null)cell(host,head,"TB/tháng");
        table.addView(head);
        for(int i=0;i<6;i++){
            String cid=host.categories[i]; TableRow line=new TableRow(host); cell(host,line,host.labels[i]);
            for(int m=0;m<months.length();m++)cell(host,line,host.money(history.getJSONObject(months.getString(m)).getLong(cid)));
            if(averages!=null)cell(host,line,average(host,averages.getDouble(cid)));
            table.addView(line);
        }
        box.addView(table);
    }

    private static void cell(MainActivity host, TableRow row, String value) {
        TextView v=new TextView(host); v.setText(value); v.setTextSize(12); v.setPadding(0,host.dp(2),host.dp(6),host.dp(2)); row.addView(v);
    }

    /** Server averages may carry two decimals; show them as returned instead of rounding money on the client. */
    static String average(MainActivity host, double value) {
        long whole=(long)Math.floor(value);
        long cents=Math.round((value-whole)*100);
        if(cents==100){whole++;cents=0;}
        if(cents==0)return host.money(whole);
        String frac=String.format(Locale.ROOT,"%02d",cents).replaceAll("0+$","");
        return host.money(whole).replace(" đ","."+frac+" đ");
    }

    static String factor(double value) {
        return String.format(Locale.US,"%.4f",value).replaceAll("0+$","").replaceAll("\\.$","");
    }
}
