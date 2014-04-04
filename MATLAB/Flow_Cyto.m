%Flow Cytometry Matlab Code.

function FlowCytoParticleAnalyzer( varargin )
%disp('COM Event handled - New Buffer')

 %Get the image from channel 1
 %Change to appropriate channel if necessary
 brightfield = varargin{end -1}.Source.getImage(1);
 intensity = varargin{end -1}.Source.getImage(2);

    %Reshapes the buffer into an image that is pixelsPerLine x
    %linesPerFrame
    brightfield = reshape(brightfield(1:varargin{end -1}.Source.pixelsPerLine* ...
        varargin{end -1}.Source.linesPerFrame),varargin{end -1}.Source.pixelsPerLine,...
        varargin{end -1}.Source.linesPerFrame);
    brightfield = brightfield';
    
    %Reshapes the buffer into an image that is pixelsPerLine x
    %linesPerFrame
    intensity = reshape(intensity(1:varargin{end -1}.Source.pixelsPerLine* ...
        varargin{end -1}.Source.linesPerFrame),varargin{end -1}.Source.pixelsPerLine,...
        varargin{end -1}.Source.linesPerFrame);
    intensity = intensity';
    
    figure(1)
    imshow(brightfield);
    figure(2)
    imshow(intensity);
    
%     %Displays the figure in figure1
%     figure(1)
%     minParticleSize = 1000;
%     maxParticleSize = 2300;
%     gaussianSigma = 2.2;
%     [ brightfield_mask, particlesPixelCount ] = createBFMask( brightfield, minParticleSize, maxParticleSize, gaussianSigma);
%     figure(1)
%     imshow(brightfield_mask)
%     
%     %threshold intensity image
%     intensity_thresholded = uint8(intensity) > 30;
% 
%     %multiply mask with intensity image to find intensity INSIDE particles
%     intensity_masked = immultiply(brightfield_mask, intensity_thresholded);
%     
%     %compute ratio
%     ratioValues = computeRatio( brightfield_mask, intensity_masked );
%     
%     %gate particle
%     if(~isempty(ratioValues))
%         for index = 1:size(ratioValues)
%             if (ratioValues(index) < ratioUpperLimit) && (ratioValues(index) > ratioLowerLimit)
%                 captures = captures+1;
%                 break;
%             end
%         end
%     end
% 
%     figure(3)
%     subplot(2,2,1)
%     imshow(brightfield);
%     subplot(2,2,2)
%     imshow(brightfield_mask);
%     subplot(2,2,3)
%     imshow(intensity);
%     subplot(2,2,4)
%     imshow(intensity_masked);
    

end
