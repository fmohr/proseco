package de.upb.crc901.proseco.commons.html;

import de.upb.crc901.proseco.commons.html.UIElement;

/**
 * HTML inline script element &lt;script\&gt;
 *
 * @author Kadiray
 *
 */
public class Script extends UIElement {
 
    private static final String TAG = "script";
 
    public Script() {
        setTag(TAG);
    }
 
    public Script(String content) {
        setTag(TAG);
        setContent(content);
    }
 
    @Override
    public String toHTML() {
        StringBuilder html = new StringBuilder("<");
        html.append(getTag());
        html.append(">\n");
        html.append(getContent());
        html.append("\n</").append(getTag()).append(">");
        return html.toString();
    }
}