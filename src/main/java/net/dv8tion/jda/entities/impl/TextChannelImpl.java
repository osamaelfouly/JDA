/**
 *    Copyright 2015-2016 Austin Keener & Michael Ritter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.dv8tion.jda.entities.impl;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import net.dv8tion.jda.JDA;
import net.dv8tion.jda.JDAInfo;
import net.dv8tion.jda.MessageBuilder;
import net.dv8tion.jda.Permission;
import net.dv8tion.jda.entities.*;
import net.dv8tion.jda.exceptions.PermissionException;
import net.dv8tion.jda.handle.EntityBuilder;
import net.dv8tion.jda.managers.ChannelManager;
import net.dv8tion.jda.utils.PermissionUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TextChannelImpl implements TextChannel
{
    private final String id;
    private final Guild guild;
    private String name;
    private String topic;
    private int position;
    private final Map<User, PermissionOverride> userPermissionOverrides = new HashMap<>();
    private final Map<Role, PermissionOverride> rolePermissionOverrides = new HashMap<>();

    public TextChannelImpl(String id, Guild guild)
    {
        this.id = id;
        this.guild = guild;
    }

    @Override
    public JDA getJDA()
    {
        return guild.getJDA();
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getTopic()
    {
        return topic;
    }

    @Override
    public Guild getGuild()
    {
        return guild;
    }

    @Override
    public List<User> getUsers()
    {
        List<User> users = getGuild().getUsers().stream().filter(user -> checkPermission(user, Permission.MESSAGE_READ)).collect(Collectors.toList());
        return Collections.unmodifiableList(users);
    }

    @Override
    public int getPosition()
    {
        return position;
    }

    @Override
    public Message sendMessage(String text)
    {
        return sendMessage(new MessageBuilder().appendString(text).build());
    }

    @Override
    public Message sendMessage(Message msg)
    {
        SelfInfo self = getJDA().getSelfInfo();
        if (!checkPermission(self, Permission.MESSAGE_WRITE))
            throw new PermissionException(Permission.MESSAGE_WRITE);
        //TODO: PermissionException for Permission.MESSAGE_ATTACH_FILES maybe

        JDAImpl api = (JDAImpl) getJDA();
        try
        {
            JSONObject response = api.getRequester().post("https://discordapp.com/api/channels/" + getId() + "/messages",
                    new JSONObject().put("content", msg.getRawContent()).put("tts", msg.isTTS()));
            return new EntityBuilder(api).createMessage(response);
        }
        catch (JSONException ex)
        {
            ex.printStackTrace();
            //sending failed
            return null;
        }
    }

    @Override
    public Message sendFile(File file)
    {
        JDAImpl api = (JDAImpl) getJDA();
        try
        {
            HttpResponse<JsonNode> response = Unirest.post("https://discordapp.com/api/channels/" + getId() + "/messages")
                    .header("authorization", getJDA().getAuthToken())
                    .header("user-agent", JDAInfo.GITHUB + " " + JDAInfo.VERSION)
                    .field("file", file)
                    .asJson();

            JSONObject messageJson = new JSONObject(response.getBody().toString());
            return new EntityBuilder(api).createMessage(messageJson);
        }
        catch (UnirestException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void sendFileAsync(File file, Consumer<Message> callback)
    {
        Thread thread = new Thread(() ->
        {
            Message message = sendFile(file);
            if (callback != null)
                callback.accept(message);
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void sendTyping()
    {
        ((JDAImpl) getJDA()).getRequester().post("https://discordapp.com/api/channels/" + getId() + "/typing", new JSONObject());
    }

    @Override
    public boolean checkPermission(User user, Permission perm)
    {
        return PermissionUtil.checkPermission(this, user, perm);
    }

    @Override
    public ChannelManager getManager()
    {
        return new ChannelManager(this);
    }


    public TextChannelImpl setName(String name)
    {
        this.name = name;
        return this;
    }

    public TextChannelImpl setTopic(String topic)
    {
        this.topic = topic;
        return this;
    }

    public TextChannelImpl setPosition(int position)
    {
        this.position = position;
        return this;
    }

    public Map<User, PermissionOverride> getUserPermissionOverrides()
    {
        return userPermissionOverrides;
    }

    public Map<Role, PermissionOverride> getRolePermissionOverrides()
    {
        return rolePermissionOverrides;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof TextChannel))
            return false;
        TextChannel oTChannel = (TextChannel) o;
        return this == oTChannel || this.getId().equals(oTChannel.getId());
    }

    @Override
    public int hashCode()
    {
        return getId().hashCode();
    }
}
