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

import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate;
import com.smartdevicelink.managers.screen.SoftButtonObject;
import com.smartdevicelink.managers.screen.SoftButtonState;
import com.smartdevicelink.managers.screen.choiceset.ChoiceCell;
import com.smartdevicelink.managers.screen.choiceset.ChoiceSet;
import com.smartdevicelink.managers.screen.choiceset.ChoiceSetSelectionListener;
import com.smartdevicelink.managers.screen.menu.MenuCell;
import com.smartdevicelink.managers.screen.menu.MenuSelectionListener;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.TemplateConfiguration;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.InteractionMode;
import com.smartdevicelink.proxy.rpc.enums.Language;
import com.smartdevicelink.proxy.rpc.enums.MenuLayout;
import com.smartdevicelink.proxy.rpc.enums.PredefinedLayout;
import com.smartdevicelink.proxy.rpc.enums.PredefinedWindows;
import com.smartdevicelink.proxy.rpc.enums.StaticIconName;
import com.smartdevicelink.proxy.rpc.enums.TriggerSource;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.transport.TCPTransportConfig;
import com.smartdevicelink.util.SystemInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;


public class SdlService extends Service {

    private static final String APP_NAME = "SDL";
    private static final String APP_ID = "8678309";
    private static final int TCP_PORT = 13764;
    private static final String DEV_MACHINE_IP_ADDRESS = "m.sdl.tools";

    private SdlManager sdlManager = null;
    private static final int FOREGROUND_SERVICE_ID = 111;

    SoftButtonObject backToMainScreen;

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
            TCPTransportConfig transport = new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, false);

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
            SdlArtwork appIcon = new SdlArtwork("sdlIcon", FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

            // The manager builder sets options for your session
            SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
            builder.setAppTypes(appType);
            builder.setTransportType(transport);
            builder.setAppIcon(appIcon);
            sdlManager = builder.build();
            sdlManager.start();
        }

        return START_STICKY;
    }
    SdlArtwork artwork1 = new SdlArtwork("SdlIcon", FileType.GRAPHIC_PNG, R.drawable.ic_sdl, false);
    SdlArtwork artwork2 = new SdlArtwork("SdlIcon2", FileType.GRAPHIC_PNG, R.drawable.sdl_lockscreen_icon, false);

    private void setMainScreen () {
        TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.TEXT_WITH_GRAPHIC.toString());

        sdlManager.getScreenManager().beginTransaction();
        sdlManager.getScreenManager().changeLayout(templateConfiguration, null);
        sdlManager.getScreenManager().setTextField1("Welcome to SDL");
        sdlManager.getScreenManager().setTextField2("Open Menu to explore SDL capabilities");
        sdlManager.getScreenManager().setTextField3(null);
        sdlManager.getScreenManager().setTextField4(null);
        sdlManager.getScreenManager().setPrimaryGraphic(artwork1);
        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {

            }
        });
        setMenu();
        SoftButtonState backToMainScreenState = new SoftButtonState("MainScreen", null, new SdlArtwork(StaticIconName.BACK));
        backToMainScreen = new SoftButtonObject("BackToMainScreen", backToMainScreenState, new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                setMainScreen();
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });
    }
    MenuCell mainCellBack, mainCell1, mainCell2, mainCell3, mainCell4, mainCell5, mainCell6, mainCell7, mainCell8, mainCell9;

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
                sdlManager.getScreenManager().beginTransaction();
                sdlManager.getScreenManager().setTextField1("1. There are up to four available text fields ");
                sdlManager.getScreenManager().setTextField2("2. That can be displayed on screen,");
                sdlManager.getScreenManager().setTextField3("3. Depending on the screen layout selected");
                sdlManager.getScreenManager().setTextField4("4. Text field 4");
                sdlManager.getScreenManager().setTitle("Title Field");
                sdlManager.getScreenManager().setPrimaryGraphic(null);
                sdlManager.getScreenManager().commit(null);
                updateMenu(true);

            }
        });

         mainCell2 = new MenuCell("Set Screen Images", null, null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                TemplateConfiguration templateConfiguration = new TemplateConfiguration().setTemplate(PredefinedLayout.DOUBLE_GRAPHIC_WITH_SOFTBUTTONS.toString());
                sdlManager.getScreenManager().beginTransaction();
                sdlManager.getScreenManager().changeLayout(templateConfiguration, null);
                sdlManager.getScreenManager().setPrimaryGraphic(artwork1);
                sdlManager.getScreenManager().setSecondaryGraphic(artwork2);
                sdlManager.getScreenManager().commit(null);
                updateMenu(true);

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

            }
        });

        // SUB MENU

        MenuCell subCell1 = new MenuCell("Graphic with Text", null, null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {

            }
        });

        MenuCell subCell2 = new MenuCell("Text with Graphic", null, null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
            }
        });

        MenuCell subCell3 = new MenuCell("Double Graphic with Soft Buttons", null, null, null, null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
            }
        });
        // sub menu parent cell
         mainCell5 = new MenuCell("Screen Layouts", null, null, MenuLayout.LIST, livio, null, Arrays.asList(subCell1, subCell2, subCell3));

         mainCell6 = new MenuCell("On Screen Buttons", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
            }

        });

        mainCell7 = new MenuCell("Slider", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
            }

        });

        mainCell8 = new MenuCell("Scrollable Message", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
            }

        });

        mainCell9 = new MenuCell("Pop up Keyboards", null, null, null,null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
            }

        });

        updateMenu(false);

    }
    private void updateMenu(boolean mainScreenBack) {
        if (mainScreenBack) {
            // Send the entire menu off to be created
            sdlManager.getScreenManager().setMenu(Arrays.asList(mainCellBack, mainCell1, mainCell2, mainCell3, mainCell4, mainCell5, mainCell6, mainCell7, mainCell8, mainCell9));
        } else {
            // Send the entire menu off to be created
            sdlManager.getScreenManager().setMenu(Arrays.asList(mainCell1, mainCell2, mainCell3, mainCell4, mainCell5, mainCell6, mainCell7, mainCell8, mainCell9));
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
                    sdlManager.getScreenManager().beginTransaction();
                    sdlManager.getScreenManager().changeLayout(templateConfiguration, null);
                    sdlManager.getScreenManager().setTextField1(choiceCell.getText() + " was selected");
                    sdlManager.getScreenManager().setTextField2("Pop up menu Information TODO..");
                    sdlManager.getScreenManager().setTextField3(null);
                    sdlManager.getScreenManager().setTextField4(null);

                    sdlManager.getScreenManager().commit(null);
                    updateMenu(true);
                }

                @Override
                public void onError(String error) {
                }
            });
            sdlManager.getScreenManager().presentChoiceSet(choiceSet, InteractionMode.MANUAL_ONLY);
        }
    }
}