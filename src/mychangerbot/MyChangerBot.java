package mychangerbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import mychangerbot.task.TaskResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static mychangerbot.ServerServiceConstants.*;

public class MyChangerBot extends TelegramLongPollingBot {
    Config cfg;

    public MyChangerBot() {
        cfg = new Config();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String username = update.getMessage().getChat().getUserName();
            String messageText = update.getMessage().getText();
            //IMPORTANT to keep chatId!
            long chatId = update.getMessage().getChatId();

            try {
                parseUserMessage(messageText, username, chatId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseUserMessage(String message, String username, long chatId) throws IOException {
        switch (message){
            case COMMAND_START: startBot(username, chatId); break;
            case COMMAND_GET_SERVER_STATUS: getServerStatus(username, chatId); break;
            case COMMAND_GET_TASK: getTask(username, chatId); break;
            //stubs for now
            //TODO update to use different programs
            case COMMAND_PROGRAM_1: getTask(username, chatId); break;
            case COMMAND_PROGRAM_2: getTask(username, chatId); break;
            case COMMAND_PROGRAM_3: getTask(username, chatId); break;
        }
    }


    private String getPathUrl(HashMap<String, String> params, String url){
        List<String> queryStringList = new ArrayList<>();
        for (String key : params.keySet()) {
            queryStringList.add(key + "=" + params.get(key));
        }
        url += ("?" + String.join("&", queryStringList));

        return url;
    }

    private void getTask(String username, long chatId) throws IOException {
        HashMap<String, String> params = new HashMap<>();
        params.put("sn_id", username);
        params.put("task_q", "1");
        params.put("program_type", "0");

        String uri = cfg.getProperty("GET_TASK_URL");
        String url = getPathUrl(params, uri);
        HttpGet get = new HttpGet(url);

        //Deprecated
        //TODO refactor not to use deprecated class
        HttpClient client = new DefaultHttpClient();
        HttpResponse response = client.execute(get);
        HttpEntity entity = response.getEntity();

        String responseString = EntityUtils.toString(entity, "UTF-8");
        //Loggin response for the great justice
        log(responseString);
        ObjectMapper mapper = new ObjectMapper();
        TaskResponse tasks = mapper.readValue(responseString, TaskResponse.class);

        Random generator = new Random();
        int randomIndex = generator.nextInt(tasks.tasks.length);

        //sending back random task from the program
        String task = tasks.tasks[randomIndex].task;
        String answer;

        if (!task.isEmpty()){
            answer = task;
        }
        else{
            answer = ERROR;
        }

        SendMessage message = new SendMessage()
                .setChatId(chatId)
                .setText(answer);

        addKeyboardButton(message);

        try {
            execute(message);
            log(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void getServerStatus(String username, long chatId) throws IOException {
        //TODO add proper validation
        String adminUsername = cfg.getProperty("ADMIN_USERNAME");

        if (username.equals(adminUsername)) {
            SendMessage message = new SendMessage()
                    .setChatId(chatId)
                    .setText(getServerStatus());

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }


    private void startBot(String username, long chatId) throws IOException {
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("sn_id", username);
        params.put("program_type", "0");

        String uri = cfg.getProperty("CREATE_USER_URL");
        String url = getPathUrl(params, uri);
        HttpPost post = new HttpPost(url);

        HttpClient client = new DefaultHttpClient();
        HttpResponse response = client.execute(post);
        log(response.toString());
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");

        if (responseString.equals(USER_ADD_SUCCESS)){
            SendPhoto message = new SendPhoto()
                    .setChatId(chatId)
                    .setPhoto(LOGO_IMG)
                    .setCaption(WELCOME_MESSAGE);
                    addKeyboardButton(message);

            try {
                sendPhoto(message);
                log(responseString);

            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    //no need to do the same in different functions, creating general one
    private ReplyKeyboardMarkup keyBoardMarkupSetup(){
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        return  replyKeyboardMarkup;
    }

    private void addKeyboardButton(SendMessage message) {
        //removing keyboard not to show initial one
        ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove();
        message.setReplyMarkup(replyKeyboardRemove);
        //creating new keyboard
        ReplyKeyboardMarkup replyKeyboardMarkup = keyBoardMarkupSetup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        message.setReplyMarkup(replyKeyboardMarkup);

        KeyboardRow keyboardFirstRow = new KeyboardRow();
        //should it be in constants?
        keyboardFirstRow.add("Get new task");
        keyboard.add(keyboardFirstRow);
        replyKeyboardMarkup.setKeyboard(keyboard);
    }

    private void addKeyboardButton(SendPhoto message) {
        ReplyKeyboardMarkup replyKeyboardMarkup = keyBoardMarkupSetup();
        message.setReplyMarkup(replyKeyboardMarkup);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow keyboardFirstRow = new KeyboardRow();
        keyboardFirstRow.add(COMMAND_PROGRAM_1);
        KeyboardRow keyboardSecondRow = new KeyboardRow();
        keyboardFirstRow.add(COMMAND_PROGRAM_2);
        KeyboardRow keyboardThirdRow = new KeyboardRow();
        keyboardFirstRow.add(COMMAND_PROGRAM_3);
        keyboard.add(keyboardFirstRow);
        keyboard.add(keyboardSecondRow);
        keyboard.add(keyboardThirdRow);
        replyKeyboardMarkup.setKeyboard(keyboard);
    }

    @Override
    public String getBotUsername() {
        String botName = cfg.getProperty("BOT_NAME");
        return botName;
    }

    @Override
    public String getBotToken() {
        String botToken = cfg.getProperty("BOT_TOKEN");
        return botToken;
    }

    private void log(String botAnswer) {
        DateFormat dateFormat = new SimpleDateFormat("dd/MM/YYYY HH:mm:ss");
        Date currentDate = new Date();
        System.out.println(dateFormat.format(currentDate) + "\t Bot Answer: - " + botAnswer);
    }

    private String getServerStatus() throws IOException {
        String uri = cfg.getProperty("SERVER_URL");
        URL url = new URL( uri );
        HttpURLConnection httpConn =  (HttpURLConnection)url.openConnection();
        httpConn.setInstanceFollowRedirects( false );
        httpConn.setRequestMethod( METHOD );
        String status = "Server status: ";
        try{
            httpConn.connect();
            status += httpConn.getResponseCode();
        }catch(java.net.ConnectException e){
            status = "ERROR: SERVER IS DOWN";
        }

        return status;
    }
}