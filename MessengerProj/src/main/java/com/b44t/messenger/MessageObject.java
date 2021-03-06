/*******************************************************************************
 *
 *                          Messenger Android Frontend
 *                        (C) 2013-2016 Nikolai Kudashov
 *                           (C) 2017 Björn Petersen
 *                    Contact: r10s@b44t.com, http://b44t.com
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see http://www.gnu.org/licenses/ .
 *
 ******************************************************************************/


package com.b44t.messenger;

import android.graphics.Paint;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.util.Linkify;

import com.b44t.ui.ActionBar.Theme;
import com.b44t.ui.Components.URLSpanNoUnderline;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageObject {

    public static final int MESSAGE_SEND_STATE_SENDING = 1;
    public static final int MESSAGE_SEND_STATE_SENT = 0;
    public static final int MESSAGE_SEND_STATE_SEND_ERROR = 2;

    public TLRPC.Message messageOwner;
    public CharSequence messageText;
    public CharSequence caption;
    public MessageObject replyMessageObject;
    public int type = 1000; // 13: sticker
    public int contentType; // one of ChatActivity.ROWTYPE_MESSAGE_CELL, .ROWTYPE_ACTION_CELL or .ROWTYPE_UNREAD_CELL
    public String dateKey;
    public float audioProgress;
    public int audioProgressSec;
    public ArrayList<TLRPC.PhotoSize> photoThumbs;
    public VideoEditedInfo videoEditedInfo;
    public boolean attachPathExists;
    public boolean mediaExists;

    public boolean forceUpdate;

    private static TextPaint textPaint;
    public int lastLineWidth;
    public int textWidth;
    public int textHeight;

    private boolean layoutCreated;

    public static Pattern urlPattern;

    public static class TextLayoutBlock {
        public StaticLayout textLayout;
        public float textXOffset;
        public float textYOffset;
        public int charactersOffset;
        public int height;
    }

    private static final int LINES_PER_BLOCK = 10;

    public ArrayList<TextLayoutBlock> textLayoutBlocks;

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users, boolean generateLayout) {
        this(message, users, null, generateLayout);
    }

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users, AbstractMap<Integer, TLRPC.Chat> chats, boolean generateLayout) {
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Theme.MSG_TEXT_COLOR);
            textPaint.linkColor = Theme.MSG_LINK_TEXT_COLOR;
        }

        textPaint.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize));

        messageOwner = message;

        if (message.replyMessage != null) {
            replyMessageObject = new MessageObject(message.replyMessage, users, chats, false);
        }

        TLRPC.User fromUser = null;
        if (message.from_id > 0) {
            if (users != null) {
                fromUser = users.get(message.from_id);
            }
            if (fromUser == null) {
                fromUser = MessagesController.getInstance().getUser(message.from_id);
            }
        }

        /*if (message instanceof TLRPC.TL_messageService) {
            if (message.action != null) {
                if (message.action instanceof TLRPC.TL_messageActionChatCreate) {
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionYouCreateGroup", R.string.ActionYouCreateGroup);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("ActionCreateGroup", R.string.ActionCreateGroup), "un1", fromUser);
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    if (message.action.user_id == message.from_id) {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouLeftUser", R.string.ActionYouLeftUser);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionLeftUser", R.string.ActionLeftUser), "un1", fromUser);
                        }
                    } else {
                        TLRPC.User whoUser = null;
                        if (users != null) {
                            whoUser = users.get(message.action.user_id);
                        }
                        if (whoUser == null) {
                            whoUser = MessagesController.getInstance().getUser(message.action.user_id);
                        }
                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionYouKickUser", R.string.ActionYouKickUser), "un2", whoUser);
                        } else if (message.action.user_id == UserConfig.getClientUserId()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionKickUserYou", R.string.ActionKickUserYou), "un1", fromUser);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionKickUser", R.string.ActionKickUser), "un2", whoUser);
                            messageText = replaceWithLink(messageText, "un1", fromUser);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                    int singleUserId = messageOwner.action.user_id;
                    if (singleUserId == 0 && messageOwner.action.users.size() == 1) {
                        singleUserId = messageOwner.action.users.get(0);
                    }
                    if (singleUserId != 0) {
                        TLRPC.User whoUser = null;
                        if (users != null) {
                            whoUser = users.get(singleUserId);
                        }
                        if (whoUser == null) {
                            whoUser = MessagesController.getInstance().getUser(singleUserId);
                        }
                        if (singleUserId == message.from_id) {
                                if (isOut()) {
                                    messageText = LocaleController.getString("ActionAddUserSelfYou", R.string.ActionAddUserSelfYou);
                                } else {
                                    messageText = replaceWithLink(LocaleController.getString("ActionAddUserSelf", R.string.ActionAddUserSelf), "un1", fromUser);
                                }
                        } else {
                            if (isOut()) {
                                messageText = replaceWithLink(LocaleController.getString("ActionYouAddUser", R.string.ActionYouAddUser), "un2", whoUser);
                            } else if (singleUserId == UserConfig.getClientUserId()) {
                                messageText = replaceWithLink(LocaleController.getString("ActionAddUserYou", R.string.ActionAddUserYou), "un1", fromUser);
                            } else {
                                messageText = replaceWithLink(LocaleController.getString("ActionAddUser", R.string.ActionAddUser), "un2", whoUser);
                                messageText = replaceWithLink(messageText, "un1", fromUser);
                            }
                        }
                    } else {
                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionYouAddUser", R.string.ActionYouAddUser), "un2", message.action.users, users);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionAddUser", R.string.ActionAddUser), "un2", message.action.users, users);
                            messageText = replaceWithLink(messageText, "un1", fromUser);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouChangedPhoto", R.string.ActionYouChangedPhoto);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionChangedPhoto", R.string.ActionChangedPhoto), "un1", fromUser);
                        }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouChangedTitle", R.string.ActionYouChangedTitle).replace("un2", message.action.title);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionChangedTitle", R.string.ActionChangedTitle).replace("un2", message.action.title), "un1", fromUser);
                        }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouRemovedPhoto", R.string.ActionYouRemovedPhoto);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionRemovedPhoto", R.string.ActionRemovedPhoto), "un1", fromUser);
                        }
                } else if (message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    messageText = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, UserObject.getUserName(fromUser));
                } else if (message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                    messageText = LocaleController.getString("HistoryCleared", R.string.HistoryCleared);
                }
            }
        } else*/ if (!isMediaEmpty()) {
            if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                messageText = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
            } else if (isVideo()) {
                messageText = LocaleController.getString("AttachVideo", R.string.AttachVideo);
            } else if (isVoice()) {
                messageText = LocaleController.getString("AttachAudio", R.string.AttachAudio);
            } else if (message.media instanceof TLRPC.TL_messageMediaGeo || message.media instanceof TLRPC.TL_messageMediaVenue) {
                messageText = LocaleController.getString("AttachLocation", R.string.AttachLocation);
            } else if (message.media instanceof TLRPC.TL_messageMediaContact) {
                messageText = LocaleController.getString("AttachContact", R.string.AttachContact);
            /*} else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                messageText = LocaleController.getString("UnsupportedMedia", R.string.UnsupportedMedia);*/
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                if (isSticker()) {
                    String sch = getStrickerChar();
                    if (sch != null && sch.length() > 0) {
                        messageText = String.format("%s %s", sch, LocaleController.getString("AttachSticker", R.string.AttachSticker));
                    } else {
                        messageText = LocaleController.getString("AttachSticker", R.string.AttachSticker);
                    }
                } else if (isMusic()) {
                    messageText = LocaleController.getString("AttachMusic", R.string.AttachMusic);
                } else if (isGif()) {
                    messageText = LocaleController.getString("AttachGif", R.string.AttachGif);
                } else {
                    String name = FileLoader.getDocumentFileName(message.media.document);
                    if (name != null && name.length() > 0) {
                        messageText = name;
                    } else {
                        messageText = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                    }
                }
            }
        } else {
            messageText = message.message;
        }
        if (messageText == null) {
            messageText = "";
        }

        setType();
        //measureInlineBotButtons();

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long) (messageOwner.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);

        if (messageOwner.message != null && messageOwner.id < 0 && messageOwner.message.length() > 6 && isVideo()) {
            videoEditedInfo = new VideoEditedInfo();
            if (!videoEditedInfo.parseString(messageOwner.message)) {
                videoEditedInfo = null;
            }
        }

        generateCaption();
        if (generateLayout) {
            messageText = Emoji.replaceEmoji(messageText, textPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            generateLayout(fromUser);
        }
        layoutCreated = generateLayout;
        generateThumbs(false);
        checkMediaExistance();
    }

    public static TextPaint getTextPaint() {
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Theme.MSG_TEXT_COLOR);
            textPaint.linkColor = Theme.MSG_LINK_TEXT_COLOR;
            textPaint.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize));
        }
        return textPaint;
    }

    public void setType() {
        int oldType = type;
        if (messageOwner instanceof TLRPC.TL_message /*|| messageOwner instanceof TLRPC.TL_messageForwarded_old2*/) {
            if (isMediaEmpty()) {
                type = 0;
                if (messageText == null || messageText.length() == 0) {
                    messageText = "Empty message";
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                type = 1;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                type = 4;
            } else if (isVideo()) {
                type = 3;
            } else if (isVoice()) {
                type = 2;
            } else if (isMusic()) {
                type = 14;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                type = 12;
            /*} else if (messageOwner.media instanceof TLRPC.TL_messageMediaUnsupported) {
                type = 0;*/
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                if (messageOwner.media.document.mime_type != null) {
                    if (isGifDocument(messageOwner.media.document)) {
                        type = 8;
                    } else if (messageOwner.media.document.mime_type.equals("image/webp") && isSticker()) {
                        type = 13;
                    } else {
                        type = 9;
                    }
                } else {
                    type = 9;
                }
            }
        } /*else if (messageOwner instanceof TLRPC.TL_messageService) {
            if (messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                type = 0;
            } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                contentType = ChatActivity.ROWTYPE_ACTION_CELL;
                type = 11;
            } else if (messageOwner.action instanceof TLRPC.TL_messageEncryptedAction) {
                if (messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                    contentType = ChatActivity.ROWTYPE_ACTION_CELL;
                    type = 10;
                } else {
                    contentType = -1;
                    type = -1;
                }
            } else if (messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear) {
                contentType = -1;
                type = -1;
            } else {
                contentType = ChatActivity.ROWTYPE_ACTION_CELL;
                type = 10;
            }
        }*/
        if (oldType != 1000 && oldType != type) {
            generateThumbs(false);
        }
    }

    public void checkLayout() {
        if (!layoutCreated) {
            layoutCreated = true;
            TLRPC.User fromUser = null;
            if (isFromUser()) {
                fromUser = MessagesController.getInstance().getUser(messageOwner.from_id);
            }
            messageText = Emoji.replaceEmoji(messageText, textPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            generateLayout(fromUser);
        }
    }

    public static boolean isGifDocument(TLRPC.Document document) {
        return document != null && document.thumb != null && document.mime_type != null && (document.mime_type.equals("image/gif") || isNewGifDocument(document));
    }

    public static boolean isNewGifDocument(TLRPC.Document document) {
        if (document != null && document.mime_type != null && document.mime_type.equals("video/mp4")) {
            for (int a = 0; a < document.attributes.size(); a++) {
                if (document.attributes.get(a) instanceof TLRPC.TL_documentAttributeAnimated) {
                    return true;
                }
            }
        }
        return false;
    }

    public void generateThumbs(boolean update) {
        /*if (messageOwner instanceof TLRPC.TL_messageService) {
            if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                if (!update) {
                    photoThumbs = new ArrayList<>(messageOwner.action.photo.sizes);
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (int a = 0; a < photoThumbs.size(); a++) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(a);
                        for (int b = 0; b < messageOwner.action.photo.sizes.size(); b++) {
                            TLRPC.PhotoSize size = messageOwner.action.photo.sizes.get(b);
                            if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                continue;
                            }
                            if (size.type.equals(photoObject.type)) {
                                photoObject.location = size.location;
                                break;
                            }
                        }
                    }
                }
            }
        } else*/ if (messageOwner.media != null && !(messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                if (!update || photoThumbs != null && photoThumbs.size() != messageOwner.media.photo.sizes.size()) {
                    photoThumbs = new ArrayList<>(messageOwner.media.photo.sizes);
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (int a = 0; a < photoThumbs.size(); a++) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(a);
                        for (int b = 0; b < messageOwner.media.photo.sizes.size(); b++) {
                            TLRPC.PhotoSize size = messageOwner.media.photo.sizes.get(b);
                            if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                continue;
                            }
                            if (size.type.equals(photoObject.type)) {
                                photoObject.location = size.location;
                                break;
                            }
                        }
                    }
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                if (!(messageOwner.media.document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                    if (!update) {
                        photoThumbs = new ArrayList<>();
                        photoThumbs.add(messageOwner.media.document.thumb);
                    } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.document.thumb != null) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(0);
                        photoObject.location = messageOwner.media.document.thumb.location;
                        photoObject.w = messageOwner.media.document.thumb.w;
                        photoObject.h = messageOwner.media.document.thumb.h;
                    }
                }
            } /* else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                if (messageOwner.media.webpage.photo != null) {
                    if (!update || photoThumbs == null) {
                        photoThumbs = new ArrayList<>(messageOwner.media.webpage.photo.sizes);
                    } else if (!photoThumbs.isEmpty()) {
                        for (int a = 0; a < photoThumbs.size(); a++) {
                            TLRPC.PhotoSize photoObject = photoThumbs.get(a);
                            for (int b = 0; b < messageOwner.media.webpage.photo.sizes.size(); b++) {
                                TLRPC.PhotoSize size = messageOwner.media.webpage.photo.sizes.get(b);
                                if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                    continue;
                                }
                                if (size.type.equals(photoObject.type)) {
                                    photoObject.location = size.location;
                                    break;
                                }
                            }
                        }
                    }
                } else if (messageOwner.media.webpage.document != null) {
                    if (!(messageOwner.media.webpage.document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                        if (!update) {
                            photoThumbs = new ArrayList<>();
                            photoThumbs.add(messageOwner.media.webpage.document.thumb);
                        } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.webpage.document.thumb != null) {
                            TLRPC.PhotoSize photoObject = photoThumbs.get(0);
                            photoObject.location = messageOwner.media.webpage.document.thumb.location;
                        }
                    }
                }
            } */
        }
    }

    /*
    public CharSequence replaceWithLink(CharSequence source, String param, ArrayList<Integer> uids, AbstractMap<Integer, TLRPC.User> usersDict) {
        int start = TextUtils.indexOf(source, param);
        if (start >= 0) {
            SpannableStringBuilder names = new SpannableStringBuilder("");
            for (int a = 0; a < uids.size(); a++) {
                TLRPC.User user = null;
                if (usersDict != null) {
                    user = usersDict.get(uids.get(a));
                }
                if (user == null) {
                    user = MessagesController.getInstance().getUser(uids.get(a));
                }
                if (user != null) {
                    String name = UserObject.getUserName(user);
                    start = names.length();
                    if (names.length() != 0) {
                        names.append(", ");
                    }
                    names.append(name);
                    names.setSpan(new URLSpanNoUnderlineBold("" + user.id), start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return TextUtils.replace(source, new String[]{param}, new CharSequence[]{names});
        }
        return source;
    }

    public CharSequence replaceWithLink(CharSequence source, String param, TLObject object) {
        int start = TextUtils.indexOf(source, param);
        if (start >= 0) {
            String name;
            int id;
            if (object instanceof TLRPC.User) {
                name = UserObject.getUserName((TLRPC.User) object);
                id = ((TLRPC.User) object).id;
            } else if (object instanceof TLRPC.Chat) {
                name = ((TLRPC.Chat) object).title;
                id = -((TLRPC.Chat) object).id;
            } else {
                name = "";
                id = 0;
            }
            SpannableStringBuilder builder = new SpannableStringBuilder(TextUtils.replace(source, new String[]{param}, new String[]{name}));
            builder.setSpan(new URLSpanNoUnderlineBold("" + id), start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return builder;
        }
        return source;
    }
    */

    /*
    public String getExtension() {
        String fileName = getFileName();
        int idx = fileName.lastIndexOf('.');
        String ext = null;
        if (idx != -1) {
            ext = fileName.substring(idx + 1);
        }
        if (ext == null || ext.length() == 0) {
            ext = messageOwner.media.document.mime_type;
        }
        if (ext == null) {
            ext = "";
        }
        ext = ext.toUpperCase();
        return ext;
    }
    */

    public String getFileName() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return FileLoader.getAttachFileName(messageOwner.media.document);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            ArrayList<TLRPC.PhotoSize> sizes = messageOwner.media.photo.sizes;
            if (sizes.size() > 0) {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    return FileLoader.getAttachFileName(sizeFull);
                }
            }
        } /*else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            return FileLoader.getAttachFileName(messageOwner.media.webpage.document);
        } */
        return "";
    }

    public int getFileType() {
        if (isVideo()) {
            return FileLoader.MEDIA_DIR_VIDEO;
        } else if (isVoice()) {
            return FileLoader.MEDIA_DIR_AUDIO;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return FileLoader.MEDIA_DIR_DOCUMENT;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return FileLoader.MEDIA_DIR_IMAGE;
        }
        return FileLoader.MEDIA_DIR_CACHE;
    }

    private static boolean containsUrls(CharSequence message) {
        if (message == null || message.length() < 2 || message.length() > 1024 * 20) {
            return false;
        }

        int length = message.length();

        int digitsInRow = 0;
        int schemeSequence = 0;
        int dotSequence = 0;

        char lastChar = 0;

        for (int i = 0; i < length; i++) {
            char c = message.charAt(i);

            if (c >= '0' && c <= '9') {
                digitsInRow++;
                if (digitsInRow >= 6) {
                    return true;
                }
                schemeSequence = 0;
                dotSequence = 0;
            } else if (!(c != ' ' && digitsInRow > 0)) {
                digitsInRow = 0;
            }
            if ((c == '@' || c == '#' || c == '/') && i == 0 || i != 0 && (message.charAt(i - 1) == ' ' || message.charAt(i - 1) == '\n')) {
                return true;
            }
            if (c == ':') {
                if (schemeSequence == 0) {
                    schemeSequence = 1;
                } else {
                    schemeSequence = 0;
                }
            } else if (c == '/') {
                if (schemeSequence == 2) {
                    return true;
                }
                if (schemeSequence == 1) {
                    schemeSequence++;
                } else {
                    schemeSequence = 0;
                }
            } else if (c == '.') {
                if (dotSequence == 0 && lastChar != ' ') {
                    dotSequence++;
                } else {
                    dotSequence = 0;
                }
            } else if (c != ' ' && lastChar == '.' && dotSequence == 1) {
                return true;
            } else {
                dotSequence = 0;
            }
            lastChar = c;
        }
        return false;
    }

    public void generateLinkDescription() {
        /*if (linkDescription != null) {
            return;
        }
        if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage instanceof TLRPC.TL_webPage && messageOwner.media.webpage.description != null) {
            linkDescription = Spannable.Factory.getInstance().newSpannable(messageOwner.media.webpage.description);
            if (containsUrls(linkDescription)) {
                try {
                    Linkify.addLinks((Spannable) linkDescription, Linkify.WEB_URLS);
                } catch (Exception e) {
                    FileLog.e("messenger", e);
                }
            }
            linkDescription = Emoji.replaceEmoji(linkDescription, textPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        }*/
    }

    public void generateCaption() {
        if (caption != null) {
            return;
        }
        if (messageOwner.media != null && messageOwner.media.caption != null && messageOwner.media.caption.length() > 0) {
            caption = Emoji.replaceEmoji(messageOwner.media.caption, textPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            if (containsUrls(caption)) {
                try {
                    Linkify.addLinks((Spannable) caption, Linkify.WEB_URLS | Linkify.PHONE_NUMBERS);
                } catch (Exception e) {
                    FileLog.e("messenger", e);
                }
                addUsernamesAndHashtags(caption);
            }
        }
    }

    private static void addUsernamesAndHashtags(CharSequence charSequence) {
        try {
            if (urlPattern == null) {
                urlPattern = Pattern.compile("(^|\\s)/[a-zA-Z@\\d_]{1,255}|(^|\\s)@[a-zA-Z\\d_]{1,32}|(^|\\s)#[\\w\\.]+");
            }
            Matcher matcher = urlPattern.matcher(charSequence);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (charSequence.charAt(start) != '@' && charSequence.charAt(start) != '#' && charSequence.charAt(start) != '/') {
                    start++;
                }
                URLSpanNoUnderline url = null;
                if (charSequence.charAt(start) == '/') {
                    ;
                } else {
                    url = new URLSpanNoUnderline(charSequence.subSequence(start, end).toString());
                }
                if (url != null) {
                    ((Spannable) charSequence).setSpan(url, start, end, 0);
                }
            }
        } catch (Exception e) {
            FileLog.e("messenger", e);
        }
    }

    public static void addLinks(CharSequence messageText) {
        if (messageText instanceof Spannable && containsUrls(messageText)) {
            if (messageText.length() < 200) {
                try {
                    Linkify.addLinks((Spannable) messageText, Linkify.WEB_URLS | Linkify.PHONE_NUMBERS);
                } catch (Exception e) {
                    FileLog.e("messenger", e);
                }
            } else {
                try {
                    Linkify.addLinks((Spannable) messageText, Linkify.WEB_URLS);
                } catch (Exception e) {
                    FileLog.e("messenger", e);
                }
            }
            addUsernamesAndHashtags(messageText);
        }
    }

    private void generateLayout(TLRPC.User fromUser) {
        if (type != 0 || messageOwner.to_id == null || messageText == null || messageText.length() == 0) {
            return;
        }

        generateLinkDescription();
        textLayoutBlocks = new ArrayList<>();

        /*final boolean useManualParse = true; // false only add links to phone numbers; was used in Telegram for incoming messages that were already parsed (?)
        if (useManualParse) {*/
            addLinks(messageText);
        /*} else {
            if (messageText instanceof Spannable && messageText.length() < 200) {
                try {
                    Linkify.addLinks((Spannable) messageText, Linkify.PHONE_NUMBERS);
                } catch (Throwable e) {
                    FileLog.e("messenger", e);
                }
            }
        }*/

        /*if (messageText instanceof Spannable) {
            Spannable spannable = (Spannable) messageText;
            int count = messageOwner.entities_.size();
            URLSpan[] spans = spannable.getSpans(0, messageText.length(), URLSpan.class);
            for (int a = 0; a < count; a++) {
                TLRPC.MessageEntity entity = messageOwner.entities_.get(a);
                if (entity.length <= 0 || entity.offset < 0 || entity.offset >= messageOwner.message.length()) {
                    continue;
                } else if (entity.offset + entity.length > messageOwner.message.length()) {
                    entity.length = messageOwner.message.length() - entity.offset;
                }
                if (spans != null && spans.length > 0) {
                    for (int b = 0; b < spans.length; b++) {
                        if (spans[b] == null) {
                            continue;
                        }
                        int start = spannable.getSpanStart(spans[b]);
                        int end = spannable.getSpanEnd(spans[b]);
                        if (entity.offset <= start && entity.offset + entity.length >= start || entity.offset <= end && entity.offset + entity.length >= end) {
                            spannable.removeSpan(spans[b]);
                            spans[b] = null;
                        }
                    }
                }
                if (entity instanceof TLRPC.TL_messageEntityBold) {
                    spannable.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                    spannable.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_messageEntityCode || entity instanceof TLRPC.TL_messageEntityPre) {
                    spannable.setSpan(new TypefaceSpan(Typeface.MONOSPACE, AndroidUtilities.dp(MessagesController.getInstance().fontSize - 1)), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                    spannable.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_messageEntityMentionName) entity).user_id), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                    spannable.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (!useManualParse) {
                    String url = messageOwner.message.substring(entity.offset, entity.offset + entity.length);
                    if (entity instanceof TLRPC.TL_messageEntityHashtag || entity instanceof TLRPC.TL_messageEntityMention) {
                        spannable.setSpan(new URLSpanNoUnderline(url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (entity instanceof TLRPC.TL_messageEntityEmail) {
                        spannable.setSpan(new URLSpanReplacement("mailto:" + url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (entity instanceof TLRPC.TL_messageEntityUrl) {
                        if (!url.toLowerCase().startsWith("http")) {
                            spannable.setSpan(new URLSpan("http://" + url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else {
                            spannable.setSpan(new URLSpan(url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                        spannable.setSpan(new URLSpanReplacement(entity.url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }*/

        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            if (messageOwner.from_id > 0 && (messageOwner.to_id.channel_id != 0 || messageOwner.to_id.chat_id != 0) && !isOut()) {
                maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
            } else {
                maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
            }
        } else {
            if (messageOwner.from_id > 0 && (messageOwner.to_id.channel_id != 0 || messageOwner.to_id.chat_id != 0) && !isOut()) {
                maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122);
            } else {
                maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
            }
        }
        /*if ( (messageOwner.fwd_from != null && messageOwner.fwd_from.channel_id != 0) && !isOut()) {
            maxWidth -= AndroidUtilities.dp(20);
        }*/

        StaticLayout textLayout;

        try {
            textLayout = new StaticLayout(messageText, textPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e("messenger", e);
            return;
        }

        textHeight = textLayout.getHeight();
        int linesCount = textLayout.getLineCount();

        int blocksCount = (int) Math.ceil((float) linesCount / LINES_PER_BLOCK);
        int linesOffset = 0;
        float prevOffset = 0;

        for (int a = 0; a < blocksCount; a++) {
            int currentBlockLinesCount = Math.min(LINES_PER_BLOCK, linesCount - linesOffset);
            TextLayoutBlock block = new TextLayoutBlock();

            if (blocksCount == 1) {
                block.textLayout = textLayout;
                block.textYOffset = 0;
                block.charactersOffset = 0;
                block.height = textHeight;
            } else {
                int startCharacter = textLayout.getLineStart(linesOffset);
                int endCharacter = textLayout.getLineEnd(linesOffset + currentBlockLinesCount - 1);
                if (endCharacter < startCharacter) {
                    continue;
                }
                block.charactersOffset = startCharacter;
                try {
                    CharSequence str = messageText.subSequence(startCharacter, endCharacter);
                    block.textLayout = new StaticLayout(str, textPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    block.textYOffset = textLayout.getLineTop(linesOffset);
                    if (a != 0) {
                        block.height = (int) (block.textYOffset - prevOffset);
                    }
                    block.height = Math.max(block.height, block.textLayout.getLineBottom(block.textLayout.getLineCount() - 1));
                    prevOffset = block.textYOffset;
                } catch (Exception e) {
                    FileLog.e("messenger", e);
                    continue;
                }
                if (a == blocksCount - 1) {
                    currentBlockLinesCount = Math.max(currentBlockLinesCount, block.textLayout.getLineCount());
                    try {
                        textHeight = Math.max(textHeight, (int) (block.textYOffset + block.textLayout.getHeight()));
                    } catch (Exception e) {
                        FileLog.e("messenger", e);
                    }
                }
            }

            textLayoutBlocks.add(block);

            float lastLeft = block.textXOffset = 0;
            try {
                lastLeft = block.textXOffset = block.textLayout.getLineLeft(currentBlockLinesCount - 1);
            } catch (Exception e) {
                FileLog.e("messenger", e);
            }

            float lastLine = 0;
            try {
                lastLine = block.textLayout.getLineWidth(currentBlockLinesCount - 1);
            } catch (Exception e) {
                FileLog.e("messenger", e);
            }

            int linesMaxWidth = (int) Math.ceil(lastLine);
            int lastLineWidthWithLeft;
            int linesMaxWidthWithLeft;
            boolean hasNonRTL = false;

            if (a == blocksCount - 1) {
                lastLineWidth = linesMaxWidth;
            }

            linesMaxWidthWithLeft = lastLineWidthWithLeft = (int) Math.ceil(lastLine + lastLeft);
            if (lastLeft == 0) {
                hasNonRTL = true;
            }

            if (currentBlockLinesCount > 1) {
                float textRealMaxWidth = 0, textRealMaxWidthWithLeft = 0, lineWidth, lineLeft;
                for (int n = 0; n < currentBlockLinesCount; n++) {
                    try {
                        lineWidth = block.textLayout.getLineWidth(n);
                    } catch (Exception e) {
                        FileLog.e("messenger", e);
                        lineWidth = 0;
                    }

                    if (lineWidth > maxWidth + 100) {
                        lineWidth = maxWidth;
                    }

                    try {
                        lineLeft = block.textLayout.getLineLeft(n);
                    } catch (Exception e) {
                        FileLog.e("messenger", e);
                        lineLeft = 0;
                    }

                    block.textXOffset = Math.min(block.textXOffset, lineLeft);

                    if (lineLeft == 0) {
                        hasNonRTL = true;
                    }
                    textRealMaxWidth = Math.max(textRealMaxWidth, lineWidth);
                    textRealMaxWidthWithLeft = Math.max(textRealMaxWidthWithLeft, lineWidth + lineLeft);
                    linesMaxWidth = Math.max(linesMaxWidth, (int) Math.ceil(lineWidth));
                    linesMaxWidthWithLeft = Math.max(linesMaxWidthWithLeft, (int) Math.ceil(lineWidth + lineLeft));
                }
                if (hasNonRTL) {
                    textRealMaxWidth = textRealMaxWidthWithLeft;
                    if (a == blocksCount - 1) {
                        lastLineWidth = lastLineWidthWithLeft;
                    }
                } else if (a == blocksCount - 1) {
                    lastLineWidth = linesMaxWidth;
                }
                textWidth = Math.max(textWidth, (int) Math.ceil(textRealMaxWidth));
            } else {
                textWidth = Math.max(textWidth, Math.min(maxWidth, linesMaxWidth));
            }

            if (hasNonRTL) {
                block.textXOffset = 0;
            }

            linesOffset += currentBlockLinesCount;
        }
    }

    public boolean isOut() {
        return messageOwner.out;
    }

    public boolean isOutOwner() { // is the message an outgoing message?
        return messageOwner.out && messageOwner.from_id > 0 && !messageOwner.post;
    }

    public boolean isFromUser() { // is the message from any user? send or received?
        return messageOwner.from_id > 0 && !messageOwner.post;
    }

    public boolean isUnread() {
        return messageOwner.unread;
    }

    public boolean isContentUnread() {
        return messageOwner.media_unread;
    }

    public void setIsRead() {
        messageOwner.unread = false;
    }

    /*
    public static int getUnreadFlags(TLRPC.Message message) {
        int flags = 0;
        if (!message.unread) {
            flags |= 1;
        }
        if (!message.media_unread) {
            flags |= 2;
        }
        return flags;
    }
    */

    public void setContentIsRead() {
        messageOwner.media_unread = false;
    }

    public int getId() {
        return messageOwner.id;
    }

    public boolean isSecretPhoto() {
        return false;
    }

    public boolean isSecretMedia() {
        return false;
    }

    public static boolean isOut(TLRPC.Message message) {
        return message.out;
    }

    public long getDialogId() {
        return getDialogId(messageOwner);
    }

    public static long getDialogId(TLRPC.Message message) {
        if (message.dialog_id == 0 && message.to_id != null) {
            if (message.to_id.chat_id != 0) {
                /*if (message.to_id.chat_id < 0) {
                    message.dialog_id = AndroidUtilities.makeBroadcastId(message.to_id.chat_id);
                } else*/ {
                    message.dialog_id = -message.to_id.chat_id;
                }
            } else if (isOut(message)) {
                message.dialog_id = message.to_id.user_id;
            } else {
                message.dialog_id = message.from_id;
            }
        }
        return message.dialog_id;
    }

    public boolean isSending() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENDING /*&& messageOwner.id < 0-- EDIT BY MR, the ID is always set by us*/;
    }

    public boolean isSendError() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SEND_ERROR /*&& messageOwner.id < 0-- EDIT BY MR, the ID is always set by us*/;
    }

    public boolean isSent() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENT /*|| messageOwner.id > 0 -- EDIT BY MR, the ID is always set by us*/;
    }

    /*
    public String getDocumentName() {
        if (messageOwner.media != null && messageOwner.media.document != null) {
            return FileLoader.getDocumentFileName(messageOwner.media.document);
        }
        return "";
    }
    */

    public static boolean isStickerDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isVoiceDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    return attribute.voice;
                }
            }
        }
        return false;
    }

    public static boolean isMusicDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    return !attribute.voice;
                }
            }
        }
        return false;
    }

    public static boolean isVideoDocument(TLRPC.Document document) {
        if (document != null) {
            boolean isAnimated = false;
            boolean isVideo = false;
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    isVideo = true;
                } else if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                    isAnimated = true;
                }
            }
            return isVideo && !isAnimated;
        }
        return false;
    }

    public TLRPC.Document getDocument() {
        /*if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            return messageOwner.media.webpage.document;
        }*/
        return messageOwner.media != null ? messageOwner.media.document : null;
    }

    public static boolean isStickerMessage(TLRPC.Message message) {
        return message.media != null && message.media.document != null && isStickerDocument(message.media.document);
    }

    public static boolean isMusicMessage(TLRPC.Message message) {
        /*if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isMusicDocument(message.media.webpage.document);
        }*/
        return message.media != null && message.media.document != null && isMusicDocument(message.media.document);
    }

    public static boolean isVoiceMessage(TLRPC.Message message) {
        /*if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isVoiceDocument(message.media.webpage.document);
        }*/
        return message.media != null && message.media.document != null && isVoiceDocument(message.media.document);
    }

    public static boolean isVideoMessage(TLRPC.Message message) {
        /*if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isVideoDocument(message.media.webpage.document);
        }*/
        return message.media != null && message.media.document != null && isVideoDocument(message.media.document);
    }

    public static TLRPC.InputStickerSet getInputStickerSet(TLRPC.Message message) {
        if (message.media != null && message.media.document != null) {
            for (TLRPC.DocumentAttribute attribute : message.media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                        return null;
                    }
                    return attribute.stickerset;
                }
            }
        }
        return null;
    }

    public String getStrickerChar() {
        if (messageOwner.media != null && messageOwner.media.document != null) {
            for (TLRPC.DocumentAttribute attribute : messageOwner.media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return attribute.alt;
                }
            }
        }
        return null;
    }

    public boolean isSelectable() {
        if (type == 6 || type == 10 || type == 11) {
            return false;
        }
        return true;
    }

    public int getApproximateHeight() {
        if (type == 0) {
            int height = textHeight /*+ (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage instanceof TLRPC.TL_webPage ? AndroidUtilities.dp(100) : 0)*/;
            if (isReply()) {
                height += AndroidUtilities.dp(42);
            }
            return height;
        } else if (type == 2) {
            return AndroidUtilities.dp(72);
        } else if (type == 12) {
            return AndroidUtilities.dp(71);
        } else if (type == 9) {
            return AndroidUtilities.dp(100);
        } else if (type == 4) {
            return AndroidUtilities.dp(114);
        } else if (type == 14) {
            return AndroidUtilities.dp(82);
        } else if (type == 10) {
            return AndroidUtilities.dp(30);
        } else if (type == 11) {
            return AndroidUtilities.dp(50);
        } else if (type == 13) {
            float maxHeight = AndroidUtilities.displaySize.y * 0.4f;
            float maxWidth;
            if (AndroidUtilities.isTablet()) {
                maxWidth = AndroidUtilities.getMinTabletSide() * 0.5f;
            } else {
                maxWidth = AndroidUtilities.displaySize.x * 0.5f;
            }
            int photoHeight = 0;
            int photoWidth = 0;
            for (TLRPC.DocumentAttribute attribute : messageOwner.media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                    photoWidth = attribute.w;
                    photoHeight = attribute.h;
                    break;
                }
            }
            if (photoWidth == 0) {
                photoHeight = (int) maxHeight;
                photoWidth = photoHeight + AndroidUtilities.dp(100);
            }
            if (photoHeight > maxHeight) {
                photoWidth *= maxHeight / photoHeight;
                photoHeight = (int)maxHeight;
            }
            if (photoWidth > maxWidth) {
                photoHeight *= maxWidth / photoWidth;
            }
            return photoHeight + AndroidUtilities.dp(14);
        } else {
            int photoHeight;
            int photoWidth;

            if (AndroidUtilities.isTablet()) {
                photoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
            } else {
                photoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
            }
            photoHeight = photoWidth + AndroidUtilities.dp(100);
            if (photoWidth > AndroidUtilities.getPhotoSize()) {
                photoWidth = AndroidUtilities.getPhotoSize();
            }
            if (photoHeight > AndroidUtilities.getPhotoSize()) {
                photoHeight = AndroidUtilities.getPhotoSize();
            }
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());

            if (currentPhotoObject != null) {
                float scale = (float) currentPhotoObject.w / (float) photoWidth;
                int h = (int) (currentPhotoObject.h / scale);
                if (h == 0) {
                    h = AndroidUtilities.dp(100);
                }
                if (h > photoHeight) {
                    h = photoHeight;
                } else if (h < AndroidUtilities.dp(120)) {
                    h = AndroidUtilities.dp(120);
                }
                /*if (isSecretPhoto()) {
                    if (AndroidUtilities.isTablet()) {
                        h = (int) (AndroidUtilities.getMinTabletSide() * 0.5f);
                    } else {
                        h = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f);
                    }
                }*/
                photoHeight = h;
            }
            return photoHeight + AndroidUtilities.dp(14);
        }
    }

    public String getStickerEmoji() {
        for (int a = 0; a < messageOwner.media.document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = messageOwner.media.document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                return attribute.alt != null && attribute.alt.length() > 0 ? attribute.alt : null;
            }
        }
        return null;
    }

    public boolean isSticker() {
        if (type != 1000) {
            return type == 13;
        }
        return isStickerMessage(messageOwner);
    }

    public boolean isMusic() {
        return isMusicMessage(messageOwner);
    }

    public boolean isVoice() {
        return isVoiceMessage(messageOwner);
    }

    public boolean isVideo() {
        return isVideoMessage(messageOwner);
    }

    public boolean isGif() {
        return messageOwner.media instanceof TLRPC.TL_messageMediaDocument && isGifDocument(messageOwner.media.document);
    }

    public boolean isWebpageDocument() {
        return false;
    }

    public boolean isNewGif() {
        return messageOwner.media != null && isNewGifDocument(messageOwner.media.document);
    }

    public String getMusicTitle() {
        TLRPC.Document document;
        if (type == 0) {
            document = messageOwner.media.webpage.document;
        } else {
            document = messageOwner.media.document;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (attribute.voice) {
                    return LocaleController.formatDateAudio(messageOwner.date);
                }
                String title = attribute.title;
                if (title == null || title.length() == 0) {
                    title = FileLoader.getDocumentFileName(document);
                    if (title == null || title.length() == 0) {
                        title = LocaleController.getString("AudioUnknownTitle", R.string.AudioUnknownTitle);
                    }
                }
                return title;
            }
        }
        return "";
    }

    public int getDuration() {
        TLRPC.Document document;
        if (type == 0) {
            document = messageOwner.media.webpage.document;
        } else {
            document = messageOwner.media.document;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                return attribute.duration;
            }
        }
        return 0;
    }

    public String getMusicAuthor() {
        TLRPC.Document document;
        if (type == 0) {
            document = messageOwner.media.webpage.document;
        } else {
            document = messageOwner.media.document;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (attribute.voice) {
                    if (isOutOwner() ) {
                        return LocaleController.getString("FromSelf", R.string.FromSelf);
                    }
                    else if (messageOwner.fwd_from != null ) {
                        return messageOwner.fwd_from.m_name;
                    } else {
                        TLRPC.User user = MessagesController.getInstance().getUser(messageOwner.from_id);
                        return UserObject.getUserName(user);
                    }
                }
                String performer = attribute.performer;
                if (performer == null || performer.length() == 0) {
                    performer = LocaleController.getString("AudioUnknownArtist", R.string.AudioUnknownArtist);
                }
                return performer;
            }
        }
        return "";
    }

    public TLRPC.InputStickerSet getInputStickerSet() {
        return getInputStickerSet(messageOwner);
    }

    public boolean isForwarded() {
        return isForwardedMessage(messageOwner);
    }

    public static boolean isForwardedMessage(TLRPC.Message message) {
        return (message.flags & TLRPC.MESSAGE_FLAG_FWD) != 0;
    }

    public boolean isReply() {
        return !(replyMessageObject != null && replyMessageObject.messageOwner instanceof TLRPC.TL_messageEmpty) && (messageOwner.reply_to_msg_id != 0 || messageOwner.reply_to_random_id != 0) && (messageOwner.flags & TLRPC.MESSAGE_FLAG_REPLY) != 0;
    }

    public boolean isMediaEmpty() {
        return isMediaEmpty(messageOwner);
    }

    public static boolean isMediaEmpty(TLRPC.Message message) {
        return message == null || message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty;
    }

    public String getForwardedName() {
        if (messageOwner.fwd_from != null) {
            return messageOwner.fwd_from.m_name;
        }
        return null;
    }

    public void checkMediaExistance() {
        File cacheFile = null;
        attachPathExists = false;
        mediaExists = false;
        if (type == 1) {
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());
            if (currentPhotoObject != null) {
                File f = FileLoader.getPathToMessage(messageOwner);
                if( f != null ) {
                    mediaExists = f.exists();
                }
            }
        } else if (type == 8 || type == 3 || type == 9 || type == 2 || type == 14) {
            if (messageOwner.attachPath != null && messageOwner.attachPath.length() > 0) {
                File f = new File(messageOwner.attachPath);
                attachPathExists = f.exists();
            }
            if (!attachPathExists) {
                mediaExists = FileLoader.getPathToMessage(messageOwner).exists();
            }
        } else {
            TLRPC.Document document = getDocument();
            if (document != null) {
                mediaExists = FileLoader.getPathToAttach(document).exists();
            } else if (type == 0) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());
                if (currentPhotoObject == null) {
                    return;
                }
                if (currentPhotoObject != null) {
                    mediaExists = FileLoader.getPathToAttach(currentPhotoObject, true).exists();
                }
            }
        }

        // EDIT BY MR: we use the attachPath for the normal images
        if( (mediaExists || attachPathExists) && messageOwner.created_by_mr ) {
            attachPathExists = true;
            mediaExists = true;
        }
        // /EDIT BY MR
    }
}
