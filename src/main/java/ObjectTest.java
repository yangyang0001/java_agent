import com.alibaba.fastjson.JSON;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ObjectTest {

    public static void main(String[] args) throws InterruptedException {

        List<String> dupliList = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            int beforeSize = dupliList.size();
            addDupli(dupliList);
            List<String> taskDupliRecordList = dupliList.subList(beforeSize, dupliList.size());
            System.out.println("taskId = " + i + " taskDupliRecordList = " + JSON.toJSONString(taskDupliRecordList));
        }

    }

    public static void addDupli(List<String> dupliList) throws InterruptedException {
        dupliList.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
        Thread.sleep(1200);
    }
}
