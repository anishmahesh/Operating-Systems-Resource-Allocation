import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;


/**
 * Created by anish on 4/12/17.
 */
// This class contains the combined code for the optimistic resource manager, as well as the Banker's algorithm. The
// process method takes an argument specifying which algorithm should be used and then accordingly calls the respective
// methods.
public class ResourceAllocation {

    enum Algo {
        optimistic, bankers
    }

    // Every input activity is broken down into its components and stored as an object of this class
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

    // Every task is assigned an object of its own. It will contain a list of inputs associated with its task-num,
    // a list of its initial claims for each resource, a list for indicating how much of each
    // resource type it is holding. It also stores the statistics related to time.
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

    List<Task> taskList; // This is the base list of each task object(task num = index).
    List<Integer> resourceAvailability; // This is a list of availability of each resource type(resource num = index).
    List<Integer> resourceReleased; // This is a list storing the count of each resource type released during a cycle
    // which will be added to the available list at the end of the cycle.
    Queue<Integer> runningTasks; // A queue for the tasks that have the resources they currently require.
    Queue<Integer> blockedTasks; // A queue for the tasks that could not be granted their resource in the prev cycle.
    int cycle = 0;

    // This is the method that simulates the cpu/manager. It looks at the inputs(activities) of a task and accordingly
    // takes actions. It works on top of the two queues: runningTasks and blockedTasks. Every outer iteration is a
    // cycle. Inside that, the blockedTasks queue is first iterated over to see if any of the blocked tasks can be
    // granted its/their pending requests. After that the runningTasks queue is iterated over. Here the next input
    // activity is checked for each task one after the other. Depending on what the activity is, the corresponding
    // action is taken. For the initiate, release and compute activities, after completing the activity, the process is
    // added back to the running queue. For the request activity, it is first checked if the request can be granted. If
    // it can, then the resources are allotted and then the process is added back to the running queue. Else it is
    // blocked. At the end of the iteration, if it is FIFO, a deadlock state is checked for and if it is then the
    // lowest numbered task is aborted. Finally, the resources released during this cycle are added to the available
    // list so that they can be used next cycle. Further, the method takes the algorithm type as input and accordingly
    // performs some actions.
    private void process(Algo algo) {
        for (int i = 0; i < taskList.size(); i++)
            runningTasks.offer(i);

        while (!runningTasks.isEmpty() || !blockedTasks.isEmpty()) {

            int numBlocked = blockedTasks.size();
            int numRunning = runningTasks.size();

            // Checking the blocked tasks here first to see if any of their pending requests can be granted.
            for (int i = 0; i < numBlocked; i++) {
                Task task = taskList.get(blockedTasks.poll());
                task.waitingTime++;
                Activity activity = task.activities.get(task.activityNum);
                if (algo.equals(Algo.optimistic))
                    tryAllocationForFifo(task, activity);
                else
                    tryAllocationForBankers(task, activity);
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
                    } else
                        runningTasks.offer(taskList.indexOf(task));
                    continue;
                }
                Activity activity = task.activities.get(task.activityNum);
                if ("initiate".equalsIgnoreCase(activity.act)) {
                    int resourceType = activity.param2;
                    int claim = activity.param3;
                    int avail = resourceAvailability.get(resourceType - 1);
                    if (algo.equals(Algo.bankers) && claim > avail) {
                        System.out.println("Banker aborts task " + taskList.indexOf(task) + " before run begins:\n" +
                                "       claim for resource " + resourceType + " (" + claim + ") exceeds number of " +
                                "units present (" + avail + ")");
                        terminateTask(task);
                        task.aborted = true;
                    } else {
                        task.claims.set(activity.param2 - 1, activity.param3);
                        task.activityNum++;
                        if (isNextActivityTerminate(task))
                            terminateTask(task);
                        else
                            runningTasks.offer(taskList.indexOf(task));
                    }
                } else if ("request".equalsIgnoreCase(activity.act)) {
                    if (algo.equals(Algo.optimistic))
                        tryAllocationForFifo(task, activity);
                    else
                        tryAllocationForBankers(task, activity);
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
                    } else
                        runningTasks.offer(taskList.indexOf(task));
                }
            }

            // If the fifo algo is used, then we need to check for deadlock here.
            while (algo.equals(Algo.optimistic) && isDeadlock()) {
                abortLowestTask();
            }

            // Transfer all the resources released during this cycle to the available list.
            for (int i = 0; i < resourceReleased.size(); i++) {
                int available = resourceAvailability.get(i);
                resourceAvailability.set(i, available + resourceReleased.get(i));
                resourceReleased.set(i, 0);
            }
            cycle++;
        }
    }

    // This method aborts the lowest numbered task in case of a deadlock.
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

    // This method checks if there is a deadlock. It first makes sure there are no running tasks. Then it iterates over
    // all tasks to see if any task can be granted its request. If not then there is a deadlock.
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

    // This method checks if given the available resources for a resource type, can a request be satisfied.
    private boolean checkIfAllocationPossible(Activity activity) {
        int resourceType = activity.param2;
        int numRequested = activity.param3;
        int available = resourceAvailability.get(resourceType - 1);
        int released = resourceReleased.get(resourceType - 1);
        return available + released >= numRequested;
    }

    // This method does the resource allocation for FIFO. Since it is a simple alogrithm, it just checks if the number
    // of requested resources of a particular type is less than the available number for that. if it is, then the
    // resources are granted by incrementing the holding list of the task, and the availability list is decremented.
    private void tryAllocationForFifo(Task task, Activity activity) {
        int resourceType = activity.param2;
        int numRequested = activity.param3;
        int alreadyHolding = task.holding.get(resourceType - 1);
        int available = resourceAvailability.get(resourceType - 1);
        if (available >= numRequested) {
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

    // This method does the resource allocation for Banker's. It first has an error check where it checks if the
    // request is greater than that task's initial claim. If it is then an error message is printed and the task is
    // aborted. If it isn't then it checks whether on granting this request the system is in a safe state or not. If it
    // isn't, then the task isn't granted its request and blocked, else the request is granted.
    private void tryAllocationForBankers(Task task, Activity activity) {
        int resourceType = activity.param2;
        int numRequested = activity.param3;
        int alreadyHolding = task.holding.get(resourceType - 1);

        if (numRequested + alreadyHolding > task.claims.get(resourceType - 1)) {
            System.out.println("During cycle " + cycle + "-" + (cycle + 1) + " of Banker's algorithms\n" +
                    "   Task " + (taskList.indexOf(task) + 1) + "'s request exceeds its claim; aborted; " +
                    alreadyHolding + " units available next cycle");
            terminateTask(task);
            task.aborted = true;
            return;
        }

        int available = resourceAvailability.get(resourceType - 1);

        // Checking for safe state in this condition.
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

    // This is the method that checks for a safe state. The inputs are: a task, the resource type requested, the
    // number requested. The algorithm followed is the standard Banker's algorithm. It first grants the request to the
    // task. It then iterates over all the non-terminated processes. For each process/task, it calculates the additional
    // need by subtracting the initial claim by the resources being held. It then checks if the need is less than the
    // available count. This is done for every resource type. Once a task is found whose need for each resource type is
    // less than the availability, that task is assumed to be terminated and its held resources are added to the
    // availability list. The whole process then repeats with the newly acquired resources to see if any other task can
    // be satisfied. This way if all the tasks can be satisfied eventually, then the system is in a safe state. Else it
    // is not. Before returning, it rolls back the initial allocation of the resource to the task.
    private boolean isSafeState(Task task, int resourceType, int numRequested) {
        int alreadyHolding = task.holding.get(resourceType - 1);
        task.holding.set(resourceType - 1, alreadyHolding + numRequested);

        List<Integer> available = new ArrayList<>(resourceAvailability);
        int resourceCurrAvail = available.get(resourceType - 1);
        available.set(resourceType - 1, resourceCurrAvail - numRequested);
        List<Integer> taskPool = new ArrayList<>();
        for (int i = 0; i < taskList.size(); i++) {
            if (!taskList.get(i).terminated && !taskList.get(i).aborted)
                taskPool.add(i);
        }

        boolean success = true;
        while (!taskPool.isEmpty()) {
            if (!success) {
                task.holding.set(resourceType - 1, alreadyHolding);
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

        task.holding.set(resourceType - 1, alreadyHolding);
        return true;
    }

    // This method  terminates a given task and adds all its resource to the released resource list so that it can be
    // available in the next cycle.
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
        ResourceAllocation resourceAllocation = new ResourceAllocation();
        resourceAllocation.readInput(input);
        System.out.println("Optimistic Resource Management Output");
        System.out.println("--------------------------------------");
        resourceAllocation.process(Algo.optimistic);
        System.out.println();
        resourceAllocation.printOutput(Algo.optimistic);
        System.out.println();

        resourceAllocation.readInput(input);
        System.out.println("Banker's Algorithm Output");
        System.out.println("--------------------------------------");
        resourceAllocation.process(Algo.bankers);
        System.out.println();
        resourceAllocation.printOutput(Algo.bankers);
    }

    private void printOutput(Algo algo) {
        if (algo.equals(Algo.optimistic)) System.out.println("FIFO");
        else System.out.println("BANKER'S");
        System.out.println();
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

    // This is the method that parses the input and initializes and assigns values to all the data structures.
    private void readInput(String input) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(input));
        int numTasks = sc.nextInt();
        taskList = new ArrayList<>(numTasks);

        runningTasks = new LinkedList<>();
        blockedTasks = new LinkedList<>();
        cycle = 0;

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
