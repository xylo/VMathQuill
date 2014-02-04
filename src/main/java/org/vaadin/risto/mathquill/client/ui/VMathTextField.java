package org.vaadin.risto.mathquill.client.ui;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.ui.HTML;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.Paintable;
import com.vaadin.terminal.gwt.client.RenderSpace;
import com.vaadin.terminal.gwt.client.UIDL;
import com.vaadin.terminal.gwt.client.Util;

public class VMathTextField extends HTML implements Paintable, VMathField {

    public static final String CLASSNAME = "v-mathtextfield";

    /** The client side widget identifier */
    protected String paintableId;

    /** Reference to the server connection object. */
    private ApplicationConnection client;

    private Element innerElement;

    private boolean mixedMode = false;

    private HandlerRegistration handlerRegistration;

    private boolean hasFocus;

    private final boolean immediate = true;

    private final VMathTextFieldEventHandler eventHandler;

    private final RenderSpace innerSize;

    public VMathTextField() {
        super();
        setStyleName(CLASSNAME);

        eventHandler = new VMathTextFieldEventHandler();

        innerElement = DOM.createSpan();
        getElement().appendChild(innerElement);
        MathJsBridge.mathifyEditable(innerElement);

        // currently only the height is stored
        innerSize = new RenderSpace();
    }

    public void updateFromUIDL(UIDL uidl, ApplicationConnection client) {
        if (client.updateComponent(this, uidl, true)) {
            // no changes, no update
            return;
        }

        // should we resize at the end of update
        boolean needsResize = false;

        this.client = client;

        paintableId = uidl.getId();

        boolean serverMixedMode = uidl
                .getBooleanAttribute(Communication.ATT_MIXEDMODE);

        // if uidl contains both a new mathelement and content, the mathelement
        // takes precedence
        if (uidl.getChildByTagName(Communication.TAG_MATHELEMENT) != null) {
            UIDL newMathElement = uidl
                    .getChildByTagName(Communication.TAG_MATHELEMENT);
            String latex = newMathElement
                    .getStringAttribute(Communication.ATT_ELEMENTLATEX);

            MathElementInserter.insertNewElement(innerElement, latex);

        } else if (uidl.hasVariable(Communication.ATT_CONTENT)) {
            String serverContent = uidl
                    .getStringVariable(Communication.ATT_CONTENT);

            if (serverMixedMode != mixedMode) {
                mixedMode = serverMixedMode;

                resetInnerElement(serverContent);

                if (mixedMode) {
                    MathJsBridge.mathifyTextBox(innerElement);
                } else {
                    MathJsBridge.mathifyEditable(innerElement);
                }
            } else {
                MathJsBridge.setMathContent(innerElement, serverContent);
            }

            MathJsBridge.blurElement(innerElement);
            needsResize = true;
        }

        if (needsResize) {
            doSizeUpdates();
        }
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        handlerRegistration = Event
                .addNativePreviewHandler(new NativePreviewHandler() {
                    public void onPreviewNativeEvent(NativePreviewEvent event) {
                        handlePreviewEvent(event);
                    }

                });
    }

    @Override
    protected void onDetach() {
        removeHandler();

        // this cannot be the one focused field after it's detached
        VMathFocusHandler.removeFocus(this);
        super.onDetach();
    }

    protected void removeHandler() {
        if (handlerRegistration != null) {
            handlerRegistration.removeHandler();
        }
    }

    /**
     * Handles native events. Used for focus and size handling
     * 
     * @param event
     */
    protected void handlePreviewEvent(NativePreviewEvent event) {
        if (eventHandler.validEventTargetsThis(getElement(), event)) {
            if (!hasFocus) {
                hasFocus = true;

                // this is the currently focused field globally
                VMathFocusHandler.setFocusedMathTextField(this);
            }
            if (eventHandler.isKeyboardEvent(event)) {
                checkForSizeChanges();
            }
        } else if (hasFocus && eventHandler.shouldLoseFocusFor(event)) {
            hasFocus = false;
            fireFocusLost();
        }
    }

    /**
     * Checks if the math element has grown in height and updates accordingly.
     * Works around the way Vaadin handles undefined sizes.
     * 
     * TODO consider removing this in the future
     */
    protected void checkForSizeChanges() {
        int currentHeight = innerElement.getOffsetHeight();

        if (currentHeight != innerSize.getHeight()) {
            innerSize.setHeight(currentHeight);
            doSizeUpdates();
        }
    }

    /**
     * Handle lost focus, update new value to server etc.
     */
    protected void fireFocusLost() {
        doSizeUpdates();
        String value = MathJsBridge.getMathValue(innerElement);
        client.updateVariable(paintableId, Communication.ATT_CONTENT, value,
                immediate);
    }

    /**
     * Force Vaadin to re-calculate the parent sizes, and afterwards redraw the
     * math.
     */
    protected void doSizeUpdates() {
        Util.notifyParentOfSizeChange(this, true);

        Scheduler.get().scheduleDeferred(new ScheduledCommand() {

            public void execute() {
                MathJsBridge.updateMath(innerElement);
            }
        });
    }

    /**
     * Reset the inner math element.
     * 
     * @param serverContent
     */
    protected void resetInnerElement(String serverContent) {
        getElement().removeChild(innerElement);
        innerElement = DOM.createSpan();
        innerElement.setInnerHTML(serverContent);
        getElement().appendChild(innerElement);

    }

    public void insertNewElement(String latexCommand) {
        MathElementInserter.insertNewElement(innerElement, latexCommand);

    }

}
