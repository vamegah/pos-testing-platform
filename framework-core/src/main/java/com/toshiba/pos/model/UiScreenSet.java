// framework-core/src/main/java/com/toshiba/pos/model/UiScreenSet.java

package com.toshiba.pos.model;

import java.util.*;

/**
 * UiScreenSet — defines the screen states and their elements for the UI harness.
 * 
 * <p>This model is used by ProductAdapter to tell the UI harness which screens
 * are available and what widgets they contain.
 */
public class UiScreenSet {

    private final Set<String> screens;
    private final Map<String, List<String>> screenWidgets;

    private UiScreenSet(Set<String> screens, Map<String, List<String>> screenWidgets) {
        this.screens = Collections.unmodifiableSet(screens);
        this.screenWidgets = Collections.unmodifiableMap(screenWidgets);
    }

    public Set<String> getScreens() {
        return screens;
    }

    public List<String> getWidgets(String screen) {
        return screenWidgets.getOrDefault(screen, Collections.emptyList());
    }

    public boolean hasScreen(String screen) {
        return screens.contains(screen);
    }

    @Override
    public String toString() {
        return "UiScreenSet{screens=" + screens + ", widgets=" + screenWidgets + '}';
    }

    /**
     * Builder for UiScreenSet.
     */
    public static class Builder {
        private final Set<String> screens = new LinkedHashSet<>();
        private final Map<String, List<String>> screenWidgets = new LinkedHashMap<>();

        public Builder addScreen(String screen, String... widgets) {
            screens.add(screen);
            screenWidgets.put(screen, Arrays.asList(widgets));
            return this;
        }

        public Builder addWidget(String screen, String widget) {
            if (screens.contains(screen)) {
                List<String> widgets = screenWidgets.computeIfAbsent(screen, k -> new ArrayList<>());
                widgets.add(widget);
            }
            return this;
        }

        public UiScreenSet build() {
            return new UiScreenSet(screens, screenWidgets);
        }
    }
}