import com.edcam.Presets;
import java.util.HashSet;
import java.util.Arrays;

public final class PresetTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        check(Presets.ALL.size()==60,"60 authored recipes");
        HashSet<String> names=new HashSet<>(),recipes=new HashSet<>();
        float[] identity={1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0};
        for(Presets.Style s:Presets.ALL){
            check(names.add(s.name),"Duplicate name: "+s.name);
            check(recipes.add(Arrays.toString(Presets.matrix(s,1))),"Duplicate transform: "+s.name);
            check(Arrays.equals(identity,Presets.matrix(s,0)),"Strength zero must preserve original: "+s.name);
            check(Arrays.equals(Presets.matrix(s,0),Presets.matrix(s,-1)),"Negative strength clamps");
            check(Arrays.equals(Presets.matrix(s,1),Presets.matrix(s,2)),"High strength clamps");
            for(float n:Presets.matrix(s,.73f))check(Float.isFinite(n),"Finite transform");
            check(Presets.matrix(s,1)[18]==1,"Alpha preserved");
        }
        check(Presets.get(-99).id==0,"Invalid low id");check(Presets.get(999).id==59,"Invalid high id");
        check(Arrays.equals(identity,Presets.matrix(Presets.get(0),1)),"Original recipe identity");
        for(String group:Presets.GROUPS)if(!group.equals("Tất cả"))check(Presets.ALL.stream().filter(s->s.group.equals(group)).count()==6,"Six styles in "+group);
        System.out.println("PASS: "+checks+" checks; 60 distinct color recipes, bounds, alpha, groups and identity.");
    }
}
