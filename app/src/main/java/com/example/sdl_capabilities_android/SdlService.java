package com.example.sdl_capabilities_android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.smartdevicelink.managers.AlertCompletionListener;
import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate;
import com.smartdevicelink.managers.lockscreen.LockScreenConfig;
import com.smartdevicelink.managers.screen.AlertView;
import com.smartdevicelink.managers.screen.OnButtonListener;
import com.smartdevicelink.managers.screen.SoftButtonObject;
import com.smartdevicelink.managers.screen.SoftButtonState;
import com.smartdevicelink.managers.screen.choiceset.ChoiceCell;
import com.smartdevicelink.managers.screen.choiceset.ChoiceSet;
import com.smartdevicelink.managers.screen.choiceset.ChoiceSetSelectionListener;
import com.smartdevicelink.managers.screen.choiceset.KeyboardAutocompleteCompletionListener;
import com.smartdevicelink.managers.screen.choiceset.KeyboardCharacterSetCompletionListener;
import com.smartdevicelink.managers.screen.choiceset.KeyboardListener;
import com.smartdevicelink.managers.screen.menu.MenuCell;
import com.smartdevicelink.managers.screen.menu.MenuSelectionListener;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.RGBColor;
import com.smartdevicelink.proxy.rpc.ScrollableMessage;
import com.smartdevicelink.proxy.rpc.SetMediaClockTimer;
import com.smartdevicelink.proxy.rpc.Slider;
import com.smartdevicelink.proxy.rpc.SliderResponse;
import com.smartdevicelink.proxy.rpc.SoftButton;
import com.smartdevicelink.proxy.rpc.SubtleAlert;
import com.smartdevicelink.proxy.rpc.TemplateColorScheme;
import com.smartdevicelink.proxy.rpc.TemplateConfiguration;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.AudioStreamingIndicator;
import com.smartdevicelink.proxy.rpc.enums.ButtonName;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.InteractionMode;
import com.smartdevicelink.proxy.rpc.enums.KeyboardEvent;
import com.smartdevicelink.proxy.rpc.enums.Language;
import com.smartdevicelink.proxy.rpc.enums.MenuLayout;
import com.smartdevicelink.proxy.rpc.enums.PredefinedLayout;
import com.smartdevicelink.proxy.rpc.enums.PredefinedWindows;
import com.smartdevicelink.proxy.rpc.enums.SoftButtonType;
import com.smartdevicelink.proxy.rpc.enums.StaticIconName;
import com.smartdevicelink.proxy.rpc.enums.TriggerSource;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.TCPTransportConfig;
import com.smartdevicelink.util.DebugTool;
import com.smartdevicelink.util.SystemInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;


public class SdlService extends Service {

    private static final String APP_NAME = "SDL";
    private static final String APP_ID = "112233445566";
    private static final int TCP_PORT = 12345;
    private static final String DEV_MACHINE_IP_ADDRESS = "10.0.0.86";
    OnButtonListener onButtonListener;
    SetMediaClockTimer mediaClock;
    boolean isMediaPaused;

    private SdlManager sdlManager = null;
    private static final int FOREGROUND_SERVICE_ID = 111;

    Integer sliderPosition = 3;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate(){
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("channelId", "channelName", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Notification serviceNotification = new Notification.Builder(this, channel.getId())
                    .setChannelId(channel.getId())
                        .build();
                startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }
        if (sdlManager != null) {
            sdlManager.dispose();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (sdlManager == null) {
            //MultiplexTransportConfig transport = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
            TCPTransportConfig transport = new TCPTransportConfig(Config.CORE_PORT, Config.CORE_IP, false);

            // The app type to be used
            Vector<AppHMIType> appType = new Vector<>();
            appType.add(AppHMIType.MEDIA);

            // The manager listener helps you know when certain events that pertain to the SDL Manager happen
            SdlManagerListener listener = new SdlManagerListener() {

                @Override
                public void onStart() {
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnHMIStatus onHMIStatus = (OnHMIStatus) notification;
                            if (onHMIStatus.getWindowID() != null && onHMIStatus.getWindowID() != PredefinedWindows.DEFAULT_WINDOW.getValue()) {
                                return;
                            }
                            if (onHMIStatus.getHmiLevel() == HMILevel.HMI_FULL && onHMIStatus.getFirstRun()) {
                                setMainScreen();
                                setMenu();
                            }
                        }
                    });
                }

                @Override
                public void onDestroy() {
                    SdlService.this.stopSelf();
                }

                @Override
                public void onError(String info, Exception e) {
                }

                @Override
                public LifecycleConfigurationUpdate managerShouldUpdateLifecycle(Language language, Language hmiLanguage) {
                    return null;
                }

                @Override
                public boolean onSystemInfoReceived(SystemInfo systemInfo) {
                    // Check the SystemInfo object to ensure that the connection to the device should continue
                    return true;
                }
            };

            // Create App Icon, this is set in the SdlManager builder
            SdlArtwork appIcon = new SdlArtwork("appIcon", FileType.GRAPHIC_PNG, R.drawable.ic_sdl, true);
            LockScreenConfig lockScreenConfig = new LockScreenConfig();
            lockScreenConfig.setDisplayMode(LockScreenConfig.DISPLAY_MODE_ALWAYS);
            lockScreenConfig.enableDismissGesture(false);

            // The manager builder sets options for your session
            SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
            builder.setAppTypes(appType);
            builder.setLockScreenConfig(lockScreenConfig);
            builder.setTransportType(transport);
            builder.setAppIcon(appIcon);
            sdlManager = builder.build();
            sdlManager.start();
        }

        return START_STICKY;
    }
    SdlArtwork artwork1 = new SdlArtwork("appIcon2", FileType.GRAPHIC_PNG, R.drawable.ic_sdl, false);
    SdlArtwork artwork2 = new SdlArtwork("appIcon3", FileType.GRAPHIC_PNG, R.drawable.sdl_lockscreen_icon, false);

    private void setMainScreen () {
        TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.TEXT_WITH_GRAPHIC.toString());
        updateScreen("Welcome to SDL", "Open Menu to explore SDL capabilities", null, null, null, null, templateConfiguration, artwork1, null);
        setMenu();
    }
    MenuCell mainCellBack, mainCell1, mainCell2, mainCell3, mainCell4, mainCell5, mainCell6, mainCell7, mainCell8, mainCell9, mainCell10;

    private void setMenu () {

        // some arts
        SdlArtwork livio = new SdlArtwork("livio", FileType.GRAPHIC_PNG, R.drawable.ic_sdl, false);

        // some voice commands
        List<String> voice2 = Collections.singletonList("Cell two");

        mainCellBack = new MenuCell("Back to Main Screen", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setMainScreen();
            }
        });

         mainCell1 = new MenuCell("Main Screen Text Fields", "Secondary Text", null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setMainScreenTextFields();
            }
        });

         mainCell2 = new MenuCell("Set Screen Images", null, null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setScreenImages();
            }
        });



        mainCell3 = new MenuCell("Pop up Menu", null, null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                popUpMenu();
            }
        });

        mainCell4 = new MenuCell("Alert", null, null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setAlert();
            }
        });

        // sub menu parent cell
        mainCell5 = new MenuCell("TODO", null, null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {

            }
        });

         mainCell6 = new MenuCell("On Screen Buttons", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setOnScreenButtons();
            }

        });

        mainCell7 = new MenuCell("Slider", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setSlider();
            }

        });

        mainCell8 = new MenuCell("Scrollable Message", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setScrollableMessage();
            }

        });

        mainCell9 = new MenuCell("Pop up Keyboards", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setPopUpKeyboard();
            }

        });

        SdlArtwork carGoLogo = new SdlArtwork("carGoLogo", FileType.GRAPHIC_PNG, R.drawable.cargo_logo, false);
        SdlArtwork carGoAppIcon = new SdlArtwork("carGoAppIcon", FileType.GRAPHIC_PNG, R.drawable.cargo_app_icon, false);

        MenuCell sub10Cell1 = new MenuCell("CarGo", null, null, carGoAppIcon, carGoLogo, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setCarGo();

            }
        });

        SdlArtwork bopsLogo = new SdlArtwork("bopsLogo", FileType.GRAPHIC_PNG, R.drawable.bops_logo, false);
        SdlArtwork bopsAppIcon = new SdlArtwork("bopsAppIcon", FileType.GRAPHIC_PNG, R.drawable.bops_app_icon, false);

        MenuCell sub10Cell2 = new MenuCell("BOPS", null, null, bopsAppIcon, bopsLogo, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setBops();
            }
        });
        SdlArtwork rydeLogo = new SdlArtwork("rydeLogo", FileType.GRAPHIC_PNG, R.drawable.ryde_logo, false);
        SdlArtwork rydeAppIcon = new SdlArtwork("rydeAppIcon", FileType.GRAPHIC_PNG, R.drawable.ryde_app_icon, false);

        MenuCell sub10Cell3 = new MenuCell("Ryde", null, null, rydeAppIcon, rydeLogo, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                setRyde();
            }
        });
        mainCell10 = new MenuCell("Color Scheme Branding Examples", null, null, MenuLayout.LIST, livio, null, Arrays.asList(sub10Cell1, sub10Cell2, sub10Cell3));


        updateMenu(false);

    }
    private void updateMenu(boolean mainScreenBack) {
        // if not on Home screen, have a back to main screen button
        if (mainScreenBack) {
            sdlManager.getScreenManager().setMenu(Arrays.asList(mainCellBack, mainCell1, mainCell2, mainCell3, mainCell4, mainCell6, mainCell7, mainCell8, mainCell9, mainCell10));
        } else {
            sdlManager.getScreenManager().setMenu(Arrays.asList(mainCell1, mainCell2, mainCell3, mainCell4, mainCell6, mainCell7, mainCell8, mainCell9, mainCell10));
        }
    }
    List<ChoiceCell> choiceCellList;

    private void popUpMenu() {

        ChoiceCell cell1 = new ChoiceCell("Item 1");
        ChoiceCell cell2 = new ChoiceCell("Item 2");
        ChoiceCell cell3 = new ChoiceCell("Item 3");

        choiceCellList = new ArrayList<>(Arrays.asList(cell1, cell2, cell3));
        if (choiceCellList != null) {
            ChoiceSet choiceSet = new ChoiceSet("This is a Pop up Menu", choiceCellList, new ChoiceSetSelectionListener() {
                @Override
                public void onChoiceSelected(ChoiceCell choiceCell, TriggerSource triggerSource, int rowIndex) {
                   // (choiceCell.getText() + " was selected");
                    TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.TEXT_WITH_GRAPHIC.toString());
                    updateScreen(choiceCell.getText() + " was selected", "Open menu and select Pop up Menu to make another selection", null, null, "Pop Up Menu", null, templateConfiguration, artwork1, null);
                    updateMenu(true);
                }

                @Override
                public void onError(String error) {
                }
            });
            sdlManager.getScreenManager().presentChoiceSet(choiceSet, InteractionMode.MANUAL_ONLY);
        }
    }

    private void sliderScreen() {
        TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.GRAPHIC_WITH_TEXT_AND_SOFTBUTTONS.toString());
        SoftButtonState softButtonState = new SoftButtonState("sliderDeploy", "Redeploy Slider", null);
        SoftButtonObject.OnEventListener onEventListener = new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                setSlider();
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        };
        SoftButtonObject softButtonObject = new SoftButtonObject("SliderButton", softButtonState, onEventListener);
        updateScreen("Slider position: " + sliderPosition, null, null, null, "Slider", Collections.singletonList(softButtonObject), templateConfiguration,null,null);
        updateMenu(true);
    }

    private void setSlider() {
        Slider slider = new Slider();
        slider.setNumTicks(6);
        slider.setPosition(sliderPosition);
        slider.setSliderHeader("Header");
        slider.setSliderFooter(Collections.singletonList("Footer"));
        slider.setCancelID(5006);

        slider.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                if (response.getSuccess()) {
                    SliderResponse sliderResponse = (SliderResponse) response;
                    sliderPosition = sliderResponse.getSliderPosition();
                    sliderScreen();
                }
            }
        });
        sdlManager.sendRPC(slider);
    }

    private void setMainScreenTextFields() {
        TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.GRAPHIC_WITH_TEXT.toString());
        updateScreen("1. There are up to four available text fields ", "2. That can be displayed on screen,", "3. Depending on the screen layout selected", "4. Text field 4", "Title Field", null, templateConfiguration, artwork2, null);
        updateMenu(true);
    }

    private void setScreenImages() {
        TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.DOUBLE_GRAPHIC_WITH_SOFTBUTTONS.toString());
        updateScreen(null, null, null, null, "Screen Images", null, templateConfiguration, artwork1, artwork2);
        updateMenu(true);
    }

    private void setAlert() {
        SoftButtonState softButtonState = new SoftButtonState("State1", "Alert ", null);
        SoftButtonState softButtonState2 = new SoftButtonState("State2", "Alert with Buttons", null);
        SoftButtonState softButtonState3 = new SoftButtonState("State3", "Subtle Alert", null);
        SoftButtonState softButtonState4 = new SoftButtonState("State4", "Subtle Alert with Buttons", null);

        SoftButtonObject softButtonObject = new SoftButtonObject("Button1", softButtonState, new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                AlertView.Builder alertBuilder = new AlertView.Builder();
                alertBuilder.setIcon(artwork1);
                alertBuilder.setText("Alert TextField 1");
                alertBuilder.setSecondaryText("Alert TextField 2");
                alertBuilder.setTertiaryText("Alert TextField 3");
                AlertView alertView = alertBuilder.build();
                sdlManager.getScreenManager().presentAlert(alertView, new AlertCompletionListener() {
                    @Override
                    public void onComplete(boolean success, Integer tryAgainTime) {

                    }
                });

            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });

        SoftButtonObject softButtonObject2 = new SoftButtonObject("Button2", softButtonState2, new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                SoftButtonState softButtonStateYes = new SoftButtonState("yesState", "Yes", null);
                SoftButtonState softButtonStateNo = new SoftButtonState("noState", "No", null);
                SoftButtonObject yesButton = new SoftButtonObject("yesButton", softButtonStateYes, new SoftButtonObject.OnEventListener() {
                    @Override
                    public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                        sdlManager.getScreenManager().setTextField2("Yes button pressed");
                    }

                    @Override
                    public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

                    }
                });

                SoftButtonObject noButton = new SoftButtonObject("noButton", softButtonStateNo, new SoftButtonObject.OnEventListener() {
                    @Override
                    public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                        sdlManager.getScreenManager().setTextField2("No button pressed");

                    }

                    @Override
                    public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

                    }
                });
                List<SoftButtonObject> softButtonObjectList = Arrays.asList(yesButton, noButton);

                AlertView.Builder alertBuilder = new AlertView.Builder();
                alertBuilder.setIcon(artwork1);
                alertBuilder.setText("Alert with Buttons");
                alertBuilder.setSoftButtons(softButtonObjectList);
                AlertView alertView = alertBuilder.build();
                sdlManager.getScreenManager().presentAlert(alertView, new AlertCompletionListener() {
                    @Override
                    public void onComplete(boolean success, Integer tryAgainTime) {

                    }
                });

            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }

        });

        SoftButtonObject softButtonObject3 = new SoftButtonObject("Button3", softButtonState3, new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                SubtleAlert subtleAlert = new SubtleAlert()
                        .setAlertText1("Line 1")
                        .setAlertText2("Line 2")
                        .setCancelID(5001);
                sdlManager.addOnRPCNotificationListener(FunctionID.ON_SUBTLE_ALERT_PRESSED, new OnRPCNotificationListener() {
                    @Override
                    public void onNotified(RPCNotification notification) {
                        sdlManager.getScreenManager().setTextField2("Subtle Alert dismissed");
                    }
                });
                sdlManager.sendRPC(subtleAlert);

            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });

        SoftButtonObject softButtonObject4 = new SoftButtonObject("Button4", softButtonState4, new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                // Soft buttons
                final int softButtonId = 123; // Set it to any unique ID
                SoftButton okButton = new SoftButton(SoftButtonType.SBT_TEXT, softButtonId);
                okButton.setText("OK");

                // Set the softbuttons(s) to the subtle alert
                SubtleAlert subtleAlert = new SubtleAlert()
                        .setAlertText1("Line 1")
                        .setAlertText2("Line 2")
                        .setCancelID(5002);
                subtleAlert.setSoftButtons(Collections.singletonList(okButton));

                // This listener is only needed once, and will work for all of soft buttons you send with your subtle alert
                sdlManager.addOnRPCNotificationListener(FunctionID.ON_BUTTON_PRESS, new OnRPCNotificationListener() {
                    @Override
                    public void onNotified(RPCNotification notification) {
                        OnButtonPress onButtonPress = (OnButtonPress) notification;
                        if (onButtonPress.getCustomButtonID() == softButtonId) {
                            sdlManager.getScreenManager().setTextField2("Ok button pressed");
                        }
                    }
                });
                sdlManager.sendRPC(subtleAlert);

            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });
        List<SoftButtonObject> softButtonObjectList = Arrays.asList(softButtonObject, softButtonObject2, softButtonObject3, softButtonObject4);


        TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.NON_MEDIA.toString());

        updateMenu(true);
        updateScreen("Click on a button below to see an example of an Alert", null, null, null, "AlertScreen", softButtonObjectList, templateConfiguration, artwork1, null);

    }

    private void updateScreen(String textField1, String textField2, String textField3, String textField4, String titleField, List<SoftButtonObject> softButtonObjectList, TemplateConfiguration templateConfiguration, SdlArtwork primaryGraphic, SdlArtwork secondaryGraphic) {

        updateScreenTemplate(templateConfiguration, new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                sdlManager.getScreenManager().beginTransaction();
                sdlManager.getScreenManager().setTextField1(textField1);
                sdlManager.getScreenManager().setTextField2(textField2);
                sdlManager.getScreenManager().setTextField3(textField3);
                sdlManager.getScreenManager().setTextField4(textField4);
                if (primaryGraphic != null) {
                    sdlManager.getScreenManager().setPrimaryGraphic(primaryGraphic);
                    sdlManager.getScreenManager().setSecondaryGraphic(secondaryGraphic);
                }
                sdlManager.getScreenManager().setTitle(titleField);
                List<SoftButtonObject> updateList = softButtonObjectList != null ? softButtonObjectList : Collections.EMPTY_LIST;
                sdlManager.getScreenManager().setSoftButtonObjects(updateList);
                sdlManager.getScreenManager().commit(null);
                if (templateConfiguration.getTemplate().equals(PredefinedLayout.MEDIA.toString())) {
                    subscribeToMediaButtons();
                    sdlManager.getScreenManager().setMediaTrackTextField("MEDIA TRACK FIELD");
                }
            }
        });
    }

    TemplateColorScheme getDefaultDay () {
        RGBColor white = new RGBColor(249, 251, 254);
        RGBColor lightBlue = new RGBColor(159, 219, 237);
        RGBColor black = new RGBColor(0, 0, 0);

        TemplateColorScheme dayColorScheme = new TemplateColorScheme()
                .setBackgroundColor(white)
                .setPrimaryColor(black)
                .setSecondaryColor(lightBlue);
        return dayColorScheme;
    }

    TemplateColorScheme getDefaultNight () {
        RGBColor white = new RGBColor(249, 251, 254);
        RGBColor darkGrey = new RGBColor(33, 37, 41);
        RGBColor black = new RGBColor(0, 0, 0);
        TemplateColorScheme nightColorScheme = new TemplateColorScheme()
                .setBackgroundColor(darkGrey)
                .setPrimaryColor(white)
                .setSecondaryColor(black);
        return nightColorScheme;
    }


    private void updateScreenTemplate(TemplateConfiguration templateConfiguration, CompletionListener listener) {
        if (templateConfiguration != null) {
            if (templateConfiguration.getDayColorScheme() == null) {
                templateConfiguration.setDayColorScheme(getDefaultDay());
                templateConfiguration.setNightColorScheme(getDefaultNight());
            }
            sdlManager.getScreenManager().beginTransaction();
            sdlManager.getScreenManager().changeLayout(templateConfiguration, null);
            sdlManager.getScreenManager().commit(new CompletionListener() {
                @Override
                public void onComplete(boolean success) {
                    listener.onComplete(true);
                }
            });
        } else {
            listener.onComplete(true);
        }
    }

    private void setOnScreenButtons() {
        TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.TEXTBUTTONS_ONLY.toString());
        updateScreen(null, null, null, null, "Buttons", softButtonObjectList(),templateConfiguration, null, null);
        updateMenu(true);
    }

    private List<SoftButtonObject> softButtonObjectList() {
        SoftButtonState softButtonState1 = new SoftButtonState("state1", "Press Me", null);
        SoftButtonState softButtonState2 = new SoftButtonState("state2", "Button 2", null);
        SoftButtonState softButtonState3 = new SoftButtonState("state3", "Button 3", null);
        SoftButtonState softButtonState4 = new SoftButtonState("state4", "Button 4", null);
        SoftButtonState softButtonState5 = new SoftButtonState("state5", "Button 5", null);
        SoftButtonState softButtonState6 = new SoftButtonState("state6", "Button 6", null);
        SoftButtonState softButtonState11 = new SoftButtonState("state7", "Press me again!", null);
        SoftButtonState softButtonState111 = new SoftButtonState("state8", "Go Back", new SdlArtwork(StaticIconName.BACK));
        SoftButtonObject.OnEventListener onEventListener = new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                sdlManager.getScreenManager().setTitle(softButtonObject.getName() + " Pressed");
                if (softButtonObject.getName().equals("Button 1")) {
                    softButtonObject.transitionToNextState();
                }


            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        };

        SoftButtonObject softButtonObject1 = new SoftButtonObject("Button 1", Arrays.asList(softButtonState1, softButtonState11, softButtonState111), softButtonState1.getName(), onEventListener);
        SoftButtonObject softButtonObject2 = new SoftButtonObject("Button 2", softButtonState2, onEventListener);
        SoftButtonObject softButtonObject3 = new SoftButtonObject("Button 3", softButtonState3, onEventListener);
        SoftButtonObject softButtonObject4 = new SoftButtonObject("Button 4", softButtonState4, onEventListener);
        SoftButtonObject softButtonObject5 = new SoftButtonObject("Button 5", softButtonState5, onEventListener);
        SoftButtonObject softButtonObject6 = new SoftButtonObject("Button 6", softButtonState6, onEventListener);

        return Arrays.asList(softButtonObject1, softButtonObject2, softButtonObject3, softButtonObject4, softButtonObject5, softButtonObject6);
    }

    private void setScrollableMessage() {
        // Create Message To Display
        String scrollableMessageText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.Vestibulum mattis ullamcorper velit sed ullamcorper morbi tincidunt ornare. Purus in massa tempor nec feugiat nisl pretium fusce id. Pharetra convallis posuere morbi leo urna molestie at elementum eu. Dictum sit amet justo donec enim.";

        // Create SoftButtons
        SoftButton softButton1 = new SoftButton(SoftButtonType.SBT_TEXT, 256);
        softButton1.setText("Button 1");

        SoftButton softButton2 = new SoftButton(SoftButtonType.SBT_TEXT, 255);
        softButton2.setText("Button 2");

         // Create SoftButton Array
        List<SoftButton> softButtonList = Arrays.asList(softButton1, softButton2);

        // Create ScrollableMessage Object
        ScrollableMessage scrollableMessage = new ScrollableMessage()
                .setScrollableMessageBody(scrollableMessageText)
                .setTimeout(50000)
                .setSoftButtons(softButtonList);

        // Set cancelId
        scrollableMessage.setCancelID(50002);

         // Send the scrollable message
        sdlManager.sendRPC(scrollableMessage);
    }

    private void setCarGo() {
        RGBColor background = new RGBColor(140, 228, 242);
        RGBColor dark = new RGBColor(39, 38, 53);
        RGBColor light = new RGBColor(232, 233, 243);

        TemplateColorScheme templateColorSchemeDay = new TemplateColorScheme();

        templateColorSchemeDay.setBackgroundColor(background);
        templateColorSchemeDay.setPrimaryColor(light);
        templateColorSchemeDay.setSecondaryColor(light);
        TemplateColorScheme templateColorSchemeNight = new TemplateColorScheme();

        templateColorSchemeNight.setBackgroundColor(background);
        templateColorSchemeNight.setPrimaryColor(dark);
        templateColorSchemeNight.setSecondaryColor(dark);
        TemplateConfiguration templateConfiguration = new TemplateConfiguration();
        templateConfiguration.setTemplate(PredefinedLayout.TEXT_AND_SOFTBUTTONS_WITH_GRAPHIC.toString());
        templateConfiguration.setDayColorScheme(templateColorSchemeDay);
        templateConfiguration.setNightColorScheme(templateColorSchemeNight);
        SdlArtwork carGoMain = new SdlArtwork("carGoMain", FileType.GRAPHIC_PNG, R.drawable.cargo_main, false);

        updateScreen("CarGo", null, null, null, "CarGo", null, templateConfiguration, carGoMain, null);
        updateMenu(true);
    }

    private void setRyde() {
        RGBColor background = new RGBColor(164, 247, 181);
        RGBColor dark = new RGBColor(69, 88, 167);
        RGBColor light = new RGBColor(242, 241, 239);

        TemplateColorScheme templateColorSchemeDay = new TemplateColorScheme();

        templateColorSchemeDay.setBackgroundColor(background);
        templateColorSchemeDay.setPrimaryColor(light);
        templateColorSchemeDay.setSecondaryColor(light);
        TemplateColorScheme templateColorSchemeNight = new TemplateColorScheme();

        templateColorSchemeNight.setBackgroundColor(background);
        templateColorSchemeNight.setPrimaryColor(dark);
        templateColorSchemeNight.setSecondaryColor(dark);
        TemplateConfiguration templateConfiguration = new TemplateConfiguration();
        templateConfiguration.setTemplate(PredefinedLayout.GRAPHIC_WITH_TEXT_AND_SOFTBUTTONS.toString());
        templateConfiguration.setDayColorScheme(templateColorSchemeDay);
        templateConfiguration.setNightColorScheme(templateColorSchemeNight);
        SdlArtwork rydeMain = new SdlArtwork("rydeMain", FileType.GRAPHIC_PNG, R.drawable.ryde_main, false);

        updateScreen("Ryde", null, null, null, "Ryde", null, templateConfiguration, rydeMain, null);
        updateMenu(true);
    }

    private void setBops() {
        isMediaPaused = false;
        mediaClock = new SetMediaClockTimer().countUpFromStartTimeInterval(0, 253, AudioStreamingIndicator.PAUSE);
        sdlManager.sendRPC(mediaClock);

        RGBColor background = new RGBColor(238, 109, 173);
        RGBColor dark = new RGBColor(51, 49, 46);
        RGBColor light = new RGBColor(250, 250, 250);

        TemplateColorScheme templateColorSchemeDay = new TemplateColorScheme();

        templateColorSchemeDay.setBackgroundColor(background);
        templateColorSchemeDay.setPrimaryColor(light);
        templateColorSchemeDay.setSecondaryColor(light);
        TemplateColorScheme templateColorSchemeNight = new TemplateColorScheme();

        templateColorSchemeNight.setBackgroundColor(background);
        templateColorSchemeNight.setPrimaryColor(dark);
        templateColorSchemeNight.setSecondaryColor(dark);

        TemplateConfiguration templateConfiguration = new TemplateConfiguration();
        templateConfiguration.setTemplate(PredefinedLayout.MEDIA.toString());
        templateConfiguration.setDayColorScheme(templateColorSchemeDay);
        templateConfiguration.setNightColorScheme(templateColorSchemeNight);
        SdlArtwork bopsMain = new SdlArtwork("bopsMain", FileType.GRAPHIC_PNG, R.drawable.bops_main, false);

        updateScreen("BOPS", null, null, null, "BOPS", null, templateConfiguration, bopsMain, null);
        updateMenu(true);

        onButtonListener = new OnButtonListener() {
            @Override
            public void onPress(ButtonName buttonName, OnButtonPress buttonPress) {
                if(buttonName == ButtonName.PLAY_PAUSE) {
                    if (isMediaPaused){
                        mediaClock = new SetMediaClockTimer().resumeWithPlayPauseIndicator(AudioStreamingIndicator.PAUSE);
                        isMediaPaused = false;
                    } else {
                        mediaClock = new SetMediaClockTimer().pauseWithPlayPauseIndicator(AudioStreamingIndicator.PLAY);
                        isMediaPaused = true;
                    }
                }
                sdlManager.sendRPC(mediaClock);
            }

            @Override
            public void onEvent(ButtonName buttonName, OnButtonEvent buttonEvent) {

            }

            @Override
            public void onError(String info) {

            }
        };
    }

    private void subscribeToMediaButtons() {
        ButtonName[] buttonNames = {ButtonName.PLAY_PAUSE, ButtonName.SEEKLEFT, ButtonName.SEEKRIGHT};
        for (ButtonName buttonName : buttonNames) {
            sdlManager.getScreenManager().addButtonListener(buttonName, onButtonListener);
        }
    }

    private void keyboardScreen(String inputText) {
        TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.GRAPHIC_WITH_TEXT_AND_SOFTBUTTONS.toString());
        SoftButtonState softButtonState = new SoftButtonState("keyboard", "Redeploy Keyboard", null);
        SoftButtonObject.OnEventListener onEventListener = new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                setPopUpKeyboard();
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        };
        SoftButtonObject softButtonObject = new SoftButtonObject("keyboard", softButtonState, onEventListener);
        updateScreen("Keyboard Text: " + inputText, null, null, null, "Keyboard", Collections.singletonList(softButtonObject), templateConfiguration,null,null);
        updateMenu(true);
    }
    private void setPopUpKeyboard() {
        KeyboardListener kbList = new KeyboardListener() {
            @Override
            public void onUserDidSubmitInput(String inputText, KeyboardEvent event) {
                keyboardScreen(inputText);
                DebugTool.logInfo("Julian", " " + inputText);
            }

            @Override
            public void onKeyboardDidAbortWithReason(KeyboardEvent event) {
                keyboardScreen("No input submitted");
            }

            @Override
            public void updateAutocompleteWithInput(String currentInputText, KeyboardAutocompleteCompletionListener keyboardAutocompleteCompletionListener) {

            }

            @Override
            public void updateCharacterSetWithInput(String currentInputText, KeyboardCharacterSetCompletionListener keyboardCharacterSetCompletionListener) {

            }

            @Override
            public void onKeyboardDidSendEvent(KeyboardEvent event, String currentInputText) {

            }

            @Override
            public void onKeyboardDidUpdateInputMask(KeyboardEvent event) {

            }
        };
        sdlManager.getScreenManager().presentKeyboard("Enter text and press enter:", null, kbList);

    }
}