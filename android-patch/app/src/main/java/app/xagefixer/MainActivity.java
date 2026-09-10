package app.xagefixer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = dp(24);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.rgb(5, 7, 13));

        TextView title = new TextView(this);
        title.setText("X Age Restriction Fixer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("LSPosed / Xposed module");
        subtitle.setTextColor(Color.rgb(34, 211, 238));
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(8);
        content.addView(subtitle, subtitleParams);

        TextView instructions = new TextView(this);
        instructions.setText(buildInstructions());
        instructions.setTextColor(Color.rgb(226, 232, 240));
        instructions.setTextSize(16);
        instructions.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        instructionParams.topMargin = dp(28);
        content.addView(instructions, instructionParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        setContentView(scrollView);
    }

    private SpannableStringBuilder buildInstructions() {
        SpannableStringBuilder text = new SpannableStringBuilder();
        appendHeading(text, "What this does");
        text.append("\nNormalizes X's own TweetWithVisibilityResults response in the X app process, "
                + "then lets X use its existing image, GIF, video, and quote renderers.\n\n");

        appendHeading(text, "Requirements");
        text.append("\n• Rooted Android device\n"
                + "• LSPosed or another compatible Xposed framework\n"
                + "• Official X package com.twitter.android\n\n");

        appendHeading(text, "Enable it");
        text.append("\n1. Install this APK.\n"
                + "2. In LSPosed, enable the module for X.\n"
                + "3. Force-stop X and open it again.\n\n");

        appendHeading(text, "Privacy");
        text.append("\nThe module has no network permission and does not log, store, or transmit response data. "
                + "It only changes matching JSON in the targeted X process.\n\n");

        appendHeading(text, "Important");
        text.append("\nThis is an Xposed module, not a re-signed copy of the official X APK. "
                + "It is intended for accounts that already pass X's access checks.");
        return text;
    }

    private void appendHeading(SpannableStringBuilder text, String heading) {
        int start = text.length();
        text.append(heading);
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(), 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
