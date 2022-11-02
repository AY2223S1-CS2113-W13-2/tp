package seedu.duke.parser;

import seedu.duke.command.Database;
import seedu.duke.exceptions.*;
import seedu.duke.module.Module;
import seedu.duke.module.ModuleMapping;
import seedu.duke.timetable.Lesson;
import seedu.duke.timetable.Timetable;
import seedu.duke.ui.Ui;
import seedu.duke.university.University;
import seedu.duke.user.UserModuleMapping;
import seedu.duke.user.UserModuleMappingList;
import seedu.duke.user.UserUniversityList;
import seedu.duke.user.UserUniversityListManager;
import seedu.duke.userstorage.UserStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class UserStorageParser {
    public static void storeInfoToUserStorageByUni(String uniName, UserUniversityListManager userUniversityListManager) {
        try {
            String fileContent = convertUniIntoFileContent(uniName, userUniversityListManager);
            UserStorage.saveFileNew(uniName, fileContent);
        } catch (IOException e) {
            Ui.printExceptionMessage(e);
        }
    }

    public static void deleteUserStorageByUni(String uniName) {
        UserStorage.deleteFile(uniName);
    }

    public static String convertUniIntoFileContent(String uniName, UserUniversityListManager userUniversityListManager) {
        String fileContent = "";
        fileContent += addFavouritesToOutputString(userUniversityListManager.getUserUniversityList(uniName));
        fileContent += addModulesToOutputString(userUniversityListManager.getMyManager().get(uniName).getMyModules().getModules());
        fileContent += "#" + addLessonTimingsToOutputString(userUniversityListManager.getTtManager().getTimetableByUniversityName(uniName));
        return fileContent;
    }

    /**.
     * Method to add all lesson timings for the same school to output String
     * @param timetable stores timetable information
     * @return output String with all timetable information for one school
     */
    private static String addLessonTimingsToOutputString(Timetable timetable) {
        String output = "";
        for (Map.Entry<String, ArrayList<Lesson>> entry : timetable.getTimetable().entrySet()) {
            ArrayList<Lesson> listOfLessons = entry.getValue();
            output += addSingleLessonTimingToOutputString(listOfLessons);
        }
        return output;
    }

    /**.
     * Method to add all timing details for the same day to output String
     * @param listOfLessons stores all lessons information for the same school on the same day
     * @return output String with all timetable information for the same day
     */
    private static String addSingleLessonTimingToOutputString(ArrayList<Lesson> listOfLessons) {
        String output = "";
        for (Lesson lesson : listOfLessons) {
            output += lesson.getCode() + ";";
            output += lesson.getDay() + ";";
            output += lesson.getStartTime() + ";";
            output += lesson.getEndTime() + "%\n";
        }
        return output;
    }

    /**.
     * Method to add 'T' if university is part of user's favourite list
     * and 'F' if university is not part of user's favourite list
     * @param uni university in user's university list
     * @return string to add to output string, indicating if the university is part of user's favourite list
     */
    private static String addFavouritesToOutputString(UserUniversityList uni) {
        String output = "";
        output += uni.isFavourite() ? 'T' : 'F';
        output += "%" + "\n";
        return output;
    }

    /**.
     * Method to add modules to output string, for a particular university
     * @param modules user's saved list of modules for a particular university
     * @return String with all information of user's list of modules for the particular university
     *              where module information is separated by ";"
     *              and each line is separated by "%"
     */
    private static String addModulesToOutputString(ArrayList<UserModuleMapping> modules) {
        String output = "";
        for (UserModuleMapping module : modules) {
            assert modules.size() > 0 : "at least one module in this university";
            output += module.getPuCode() + ";";
            if (!module.getComment().equals("") && module.getComment() != null) {
                output += module.getComment() + ";";
            }
            output += "%\n";
        }
        return output;
    }

    public static UserUniversityListManager getSavedLists() {
        try {
            HashMap<String, String> filePaths = UserStorage.getFilePaths();
            UserUniversityListManager userUniversityListManager = new UserUniversityListManager();
            if (filePaths.isEmpty()) {
                return userUniversityListManager;
            }
            for (HashMap.Entry<String, String> entry : filePaths.entrySet()) {
                String fileContent = UserStorage.loadFileNew(entry.getKey());
                String uniName = entry.getKey();
                String[] splitFileContent = fileContent.split("#");
                String fileContentForUniList = splitFileContent[0];
                try {
                    UserUniversityList uniList = convertFileContentIntoUniList(fileContentForUniList, uniName);
                    userUniversityListManager.getMyManager().put(uniName, uniList);
                } catch (InvalidUserStorageFileException e) {
                    deleteUserStorageByUni(uniName);
                    filePaths.remove(entry.getKey());
                    Ui.printExceptionMessage(e);
                    continue;
                }
                if (splitFileContent.length == 2) {
                    String fileContentForTimetable = splitFileContent[1];
                    try {
                        Timetable timetable = convertFileContentIntoTimetable(fileContentForTimetable, uniName);
                        userUniversityListManager.getTtManager().getTimetableManager().put(uniName, timetable);
                    } catch (InvalidUserStorageFileException e) {
                        deleteUserStorageByUni(uniName);
                        filePaths.remove(entry.getKey());
                        userUniversityListManager.getMyManager().remove(uniName);
                        Ui.printExceptionMessage(e);
                    }
                } else {
                    userUniversityListManager.getTtManager().getTimetableManager().put(uniName, new Timetable());
                }
            }
            return userUniversityListManager;
        } catch (IOException | InvalidUniversityException | InvalidModuleException e) {
            Ui.printExceptionMessage(e);
        }
        return new UserUniversityListManager();
    }

    public static UserUniversityList convertFileContentIntoUniList(String fileContent, String uniName)
            throws InvalidUserStorageFileException, InvalidUniversityException {
        UserUniversityList newUni = new UserUniversityList(uniName);
        String[] items = splitLineInFileContent(fileContent);
        if (!isValidUniFormat(items)) {
            throw new InvalidUserStorageFileException("Invalid file format\n" + getDeleteMessage(uniName));
        }
        String isFavourite = items[0];
        UserModuleMappingList moduleList = new UserModuleMappingList();
        getModuleInfoFromString(items, moduleList, uniName);
        newUni.setMyModules(moduleList);
        setFavourite(newUni, isFavourite);
        return newUni;
    }

    private static String getDeleteMessage(String uniName) {
        return "Deleted university list and timetable for " + uniName + " from storage\n";
    }

    public static Timetable convertFileContentIntoTimetable(String fileContent, String uniName)
            throws InvalidUserStorageFileException, InvalidUniversityException, InvalidModuleException {
        Timetable timetable = new Timetable();
        String[] lessons = splitLineInFileContent(fileContent);
        for (String lesson : lessons) {
            String[] details = splitModuleInformationInFileContent(lesson);
            if (!isValidTimetableFormat(details)) {
                throw new InvalidUserStorageFileException("Invalid file format\n" + getDeleteMessage(uniName));
            }
            String moduleCode = details[0];
            ModuleMapping moduleMapping;
            try {
                moduleMapping = Database.findPuMapping(moduleCode, uniName);
            } catch (ModuleNotFoundException | UniversityNotFoundException e) {
                throw new InvalidUserStorageFileException("Invalid module code " + moduleCode + " for " + uniName + "\n"
                        + getDeleteMessage(uniName));
            }
            Module puModule = moduleMapping.getPartnerUniversityModule();
            String day = details[1];
            String startTime = details[2];
            String endTime = details[3];
            University pu = new University(uniName, puModule.getUniversity().getCountry());
            Lesson newLesson = new Lesson(moduleCode, puModule.getTitle(), puModule.getCredit(), pu,
                    day, startTime, endTime);
            timetable.addLesson(newLesson, true);
        }
        return timetable;
    }

    private static boolean isValidTimetableFormat(String[] details) {
        return details.length == 4;
    }

    /**.
     * Method to check if file content in data/uni_info.txt is empty
     * @param fileContent string from data/uni_info.txt
     * @return true if file content is empty
     */
    private static boolean isFileContentEmpty(String fileContent) {
        return fileContent.equals("");
    }

    /**.
     * Method to split file content by line, using regex "%"
     * @param uni string containing PU information, separated by "%"
     * @return array of strings, holding PU information ie. PU name and modules
     */
    private static String[] splitLineInFileContent(String uni) {
        return uni.split("%");
    }

    private static boolean isValidUniFormat(String[] items) {
        return items.length >= 1 && (items[0].equals("T") || items[0].equals("F"));
    }

    /**.
     * Method to indicate in UserUniversityList that this particular university is part of
     * user's favourite list previously
     * @param newUni university that is part of user's university list
     * @param isFavourite 'T' if university is part of user's favourite list,
     *                    'F' otherwise
     */
    private static void setFavourite(UserUniversityList newUni, String isFavourite) {
        if (isFavourite.equals("T")) {
            newUni.setFavourite(true);
        } else {
            newUni.setFavourite(false);
        }
    }

    /**.
     * Method to get module information from string, and store into UserModuleMappingList
     * @param items array of strings, where the first element is the partner university's name,
     *              followed by a list of PU modules that the user is interested in
     * @param moduleList list of PU modules that the user is interested in
     * @throws InvalidUserStorageFileException when the String in data/uni_info.txt is in the wrong format
     */
    private static void getModuleInfoFromString(String[] items, UserModuleMappingList moduleList, String puName)
            throws InvalidUserStorageFileException {
        for (int i = 1; i < items.length; ++i) {
            assert items.length > 1 : "This university has at least one module saved";
            String[] details = splitModuleInformationInFileContent(items[i]);
            if (!isValidModulesFormat(details)) {
                throw new InvalidUserStorageFileException("Invalid file format");
            }
            String moduleCode = details[0];
            ModuleMapping moduleMapping;
            try {
                moduleMapping = Database.findPuMapping(moduleCode, puName);
            } catch (ModuleNotFoundException | UniversityNotFoundException e) {
                throw new InvalidUserStorageFileException("Invalid module code " + moduleCode + " for " + puName  + "\n"
                        + getDeleteMessage(puName));
            }
            Module puModule = moduleMapping.getPartnerUniversityModule();
            Module nusModule = moduleMapping.getNusModule();
            UserModuleMapping userModuleToAdd = new UserModuleMapping(puModule.getCode(),
                    puModule.getTitle(), nusModule.getCode(), nusModule.getTitle(),
                    nusModule.getCredit(), puModule.getCredit(), puModule.getUniversity().getName(),
                    puModule.getUniversity().getCountry());
            if (details.length == 2) {
                userModuleToAdd.setComment(details[1]);
            }
            moduleList.addModule(userModuleToAdd, true);
        }
    }



    /**.
     * Method to split module information, using regex ";"
     * @param moduleInfo string containing 6 fields of module information, separated by ";"
     * @return array of strings, holding module information ie. PU module code, PU module name etc.
     */
    private static String[] splitModuleInformationInFileContent(String moduleInfo) {
        return moduleInfo.split(";");
    }

    /**.
     * Method to check if module information is saved in a valid format in data/uni_info.txt
     * ie. must have 6 fields of information, corresponding to each module
     * @param details array of strings, holding module information
     * @return true if it is a valid format
     */
    private static boolean isValidModulesFormat(String[] details) {
        return details.length == 1 || details.length == 2;
    }
}
