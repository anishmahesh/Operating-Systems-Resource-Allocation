import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by anish on 4/12/17.
 */
public class Bankers {
    class Activity {
        String act;
        int taskNum;
        int param2;
        int param3;

        public Activity(String act, int taskNum, int param2, int param3) {
            this.act = act;
            this.taskNum = taskNum;
            this.param2 = param2;
            this.param3 = param3;
        }
    }

    class Task {
        List<Integer> claims;
        List<Integer> holding;
        List<Activity> activities;
        int waitingTime = 0;
        int totalTime;
        int activityNum = 0;
        int computeRemaining = 0;
        boolean aborted = false;
        boolean terminated = false;
        boolean compute = false;

        public Task(int numResourceTypes) {
            claims = new ArrayList<>(numResourceTypes);
            holding = new ArrayList<>(numResourceTypes);
            for (int i = 0; i < numResourceTypes; i++) {
                claims.add(0);
                holding.add(0);
            }
            activities = new ArrayList<>();
        }
    }

    List<Task> taskList;
    List<Integer> resourceAvailability;
    List<Integer> resourceReleased;
    Queue<Integer> runningTasks = new LinkedList<>();
    Queue<Integer> blockedTasks = new LinkedList<>();
    int cycle = 0;

    private void process() {
        for (int i = 0; i < taskList.size(); i++)
            runningTasks.offer(i);

        while (!runningTasks.isEmpty() || !blockedTasks.isEmpty()) {

            int numBlocked = blockedTasks.size();
            int numRunning = runningTasks.size();

            for (int i = 0; i < numBlocked; i++) {
                Task task = taskList.get(blockedTasks.poll());
                task.waitingTime++;
                Activity activity = task.activities.get(task.activityNum);
                tryAllocation(task, activity);
            }

            for (int i = 0; i < numRunning; i++) {
                Task task = taskList.get(runningTasks.poll());
                if (task.compute) {
                    task.computeRemaining--;
                    if (task.computeRemaining == 0) {
                        task.compute = false;
                        if (isNextActivityTerminate(task))
                            terminateTask(task);
                        else
                            runningTasks.offer(taskList.indexOf(task));
                    }
                    else
                        runningTasks.offer(taskList.indexOf(task));
                    continue;
                }
                Activity activity = task.activities.get(task.activityNum);
                if ("initiate".equalsIgnoreCase(activity.act)) {
                    int resourceType = activity.param2;
                    int claim = activity.param3;
                    int avail = resourceAvailability.get(resourceType-1);
                    if (claim > avail) {
                        System.out.println("Banker aborts task "+taskList.indexOf(task)+" before run begins:\n" +
                                "       claim for resource "+resourceType+" ("+claim+") exceeds number of " +
                                "units present ("+avail+")");
                        terminateTask(task);
                        task.aborted = true;
                    }
                    else {
                        task.claims.set(activity.param2 - 1, activity.param3);
                        task.activityNum++;
                        if (isNextActivityTerminate(task))
                            terminateTask(task);
                        else
                            runningTasks.offer(taskList.indexOf(task));
                    }
                } else if ("request".equalsIgnoreCase(activity.act)) {
                    tryAllocation(task, activity);
                } else if ("release".equalsIgnoreCase(activity.act)) {
                    int resourceType = activity.param2;
                    int numReleasing = activity.param3;
                    int currentlyReleased = resourceReleased.get(resourceType - 1);
                    resourceReleased.set(resourceType - 1, currentlyReleased + numReleasing);
                    int holding = task.holding.get(resourceType - 1);
                    task.holding.set(resourceType - 1, holding - numReleasing);
                    task.activityNum++;
                    if (isNextActivityTerminate(task))
                        terminateTask(task);
                    else
                        runningTasks.offer(taskList.indexOf(task));
                } else if ("compute".equalsIgnoreCase(activity.act)) {
                    int numCycles = activity.param2;
                    task.compute = true;
                    task.computeRemaining = numCycles - 1;
                    task.activityNum++;
                    if (task.computeRemaining == 0) {
                        task.compute = false;
                        if (isNextActivityTerminate(task))
                            terminateTask(task);
                        else
                            runningTasks.offer(taskList.indexOf(task));
                    }
                    else
                        runningTasks.offer(taskList.indexOf(task));
                }
            }

//            while (isDeadlock()) {
//                abortLowestTask();
//            }

            for (int i = 0; i < resourceReleased.size(); i++) {
                int available = resourceAvailability.get(i);
                resourceAvailability.set(i, available + resourceReleased.get(i));
                resourceReleased.set(i, 0);
            }
            cycle++;
        }
    }

    private void abortLowestTask() {
        for (int i = 0; i < taskList.size(); i++) {
            if (blockedTasks.contains(i)) {
                terminateTask(taskList.get(i));
                blockedTasks.remove(i);
                taskList.get(i).aborted = true;
                break;
            }
        }
    }

    private boolean isDeadlock() {
        if (!runningTasks.isEmpty() || (runningTasks.isEmpty() && blockedTasks.isEmpty()))
            return false;
        else {
            for (Task task : taskList) {
                if (!task.aborted && !task.terminated) {
                    Activity activity = task.activities.get(task.activityNum);
                    if (checkIfAllocationPossible(activity))
                        return false;
                }
            }
        }
        return true;
    }

    private boolean checkIfAllocationPossible(Activity activity) {
        int resourceType = activity.param2;
        int numRequested = activity.param3;
        int available = resourceAvailability.get(resourceType - 1);
        int released = resourceReleased.get(resourceType - 1);
        return available + released >= numRequested;
    }

    private void tryAllocation(Task task, Activity activity) {
        int resourceType = activity.param2;
        int numRequested = activity.param3;
        int alreadyHolding = task.holding.get(resourceType-1);

        if (numRequested + alreadyHolding > task.claims.get(resourceType-1)) {
            System.out.println("During cycle "+cycle+"-"+(cycle+1)+" of Banker's algorithms\n" +
                    "   Task "+(taskList.indexOf(task)+1)+"'s request exceeds its claim; aborted; " +
                    alreadyHolding + " units available next cycle");
            terminateTask(task);
            task.aborted = true;
            return;
        }

        int available = resourceAvailability.get(resourceType - 1);
        if (available >= numRequested && isSafeState(task, resourceType, numRequested)) {
            task.holding.set(resourceType - 1, alreadyHolding + numRequested);
            resourceAvailability.set(resourceType - 1, available - numRequested);
            task.activityNum++;
            if (isNextActivityTerminate(task))
                terminateTask(task);
            else
                runningTasks.offer(taskList.indexOf(task));
        } else {
            blockedTasks.offer(taskList.indexOf(task));
        }
    }

    private boolean isSafeState(Task task, int resourceType, int numRequested) {
        int alreadyHolding = task.holding.get(resourceType-1);
        task.holding.set(resourceType-1, alreadyHolding + numRequested);

        List<Integer> available = new ArrayList<>(resourceAvailability);
        int resourceCurrAvail = available.get(resourceType-1);
        available.set(resourceType-1, resourceCurrAvail - numRequested);
        List<Integer> taskPool = new ArrayList<>();
        for (int i = 0; i < taskList.size(); i++) {
            if (!taskList.get(i).terminated && !taskList.get(i).aborted)
                taskPool.add(i);
        }

        boolean success = true;
        while (!taskPool.isEmpty()) {
            if (!success) {
                task.holding.set(resourceType-1, alreadyHolding);
                return false;
            }
            success = false;
            for (int i = 0; i < taskPool.size(); i++) {
                Task currTask = taskList.get(taskPool.get(i));
                int j = 0;
                for (; j < available.size(); j++) {
                    int need = currTask.claims.get(j) - currTask.holding.get(j);
                    if (need > available.get(j))
                        break;
                }
                if (j == available.size()) {
                    for (int k = 0; k < available.size(); k++) {
                        available.set(k, available.get(k) + currTask.holding.get(k));
                    }
                    taskPool.remove(i);
                    success = true;
                    break;
                }
            }
        }

        task.holding.set(resourceType-1, alreadyHolding);
        return true;
    }

    private void terminateTask(Task task) {
        for (int i = 0; i < task.holding.size(); i++) {
            int currentlyReleased = resourceReleased.get(i);
            resourceReleased.set(i, currentlyReleased + task.holding.get(i));
            task.holding.set(i, 0);
        }
        task.totalTime = cycle + 1;
        task.terminated = true;
    }

    private boolean isNextActivityTerminate(Task task) {
        return "terminate".equalsIgnoreCase(task.activities.get(task.activityNum).act);
    }

    public static void main(String args[]) throws FileNotFoundException {
        String input = args[0];
        Bankers bankers = new Bankers();
        bankers.readInput(input);
        bankers.process();
        bankers.printOutput();
    }

    private void printOutput() {
        System.out.println("Bankers");
        int totalRun = 0, totalWait = 0;
        for (int i = 0; i < taskList.size(); i++) {
            Task task = taskList.get(i);
            if (task.aborted)
                System.out.println("Task " + (i + 1) + '\t' + "aborted");
            else {
                System.out.println("Task " + (i + 1) + '\t' + task.totalTime
                        + '\t' + task.waitingTime + '\t' + Math.round(task.waitingTime * 100.0 / task.totalTime) + "%");
                totalRun += task.totalTime;
                totalWait += task.waitingTime;
            }
        }
        System.out.println("total" + '\t' + totalRun + '\t' + totalWait + '\t'
                + Math.round(totalWait * 100.0 / totalRun) + "%");
    }

    private void readInput(String input) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(input));
        int numTasks = sc.nextInt();
        taskList = new ArrayList<>(numTasks);

        int numResourceTypes = sc.nextInt();
        resourceAvailability = new ArrayList<>(numResourceTypes);
        resourceReleased = new ArrayList<>(numResourceTypes);
        for (int i = 0; i < numResourceTypes; i++) {
            resourceAvailability.add(sc.nextInt());
            resourceReleased.add(0);
        }

        for (int i = 0; i < numTasks; i++) {
            taskList.add(new Task(numResourceTypes));
        }

        while (sc.hasNext()) {
            Activity activity = new Activity(sc.next(), sc.nextInt(), sc.nextInt(), sc.nextInt());
            taskList.get(activity.taskNum - 1).activities.add(activity);
        }

    }
}
